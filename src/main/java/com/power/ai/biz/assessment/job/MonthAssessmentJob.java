package com.power.ai.biz.assessment.job;

import com.power.ai.biz.assessment.service.MonthAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 月度考核跑批入口（0-5）。
 *
 * 职责划分：Job 只管三件事——什么时候跑、拿不拿得到锁、兜底异常；
 * 怎么算全部在 MonthAssessmentService 里。这样业务逻辑可以被手动调用验证，
 * 不用等到凌晨 1 点，也不依赖定时框架。
 *
 * 分布式锁为什么这样用：
 * 1. 为什么需要锁：生产环境多实例部署时，@Scheduled 在每个实例上都会触发，
 *    没有锁同一个月的考核会被算 N 遍（结果虽然幂等，但告警会重复发）；
 * 2. tryLock(3, 30, SECONDS) 两个参数的含义：
 *    - waitTime=3s：等 3 秒拿不到锁说明别的实例在跑，直接放弃——跑批不需要排队；
 *    - leaseTime=30s：锁 30 秒后自动过期。即使本实例 JVM 崩溃没走到 finally，
 *      锁也不会永久锁死（这是固定租期比看门狗更适合跑批的原因：短任务要的是兜底，
 *      看门狗续期是给长业务流程准备的）；
 * 3. unlock 前先 isHeldByCurrentThread()：如果业务意外跑超 30 秒，锁已被
 *    broker 自动释放，直接 unlock 会抛 IllegalMonitorStateException。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthAssessmentJob {

    /** 锁的 key：所有实例共用同一把，谁拿到谁跑 */
    private static final String LOCK_KEY = "lock:month-assessment";

    private final RedissonClient redissonClient;
    private final MonthAssessmentService monthAssessmentService;

    /** 每天凌晨 1 点执行（cron 来自配置 app.assessment.cron，调频不用改代码） */
    @Scheduled(cron = "${app.assessment.cron}")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                log.info("其他实例正在执行考核跑批，本次跳过");
                return;
            }
            try {
                log.info("===== 月度考核跑批开始 =====");
                monthAssessmentService.runMonthlyAssessment();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待分布式锁被中断，本次跑批跳过");
        }
    }
}
