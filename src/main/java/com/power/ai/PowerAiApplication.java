package com.power.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电力智能服务平台
 *
 * 阶段0：电力业务底座（客户/台区/电表/工单/账单）
 * 阶段1：RAG 知识问答（双路召回）
 * 阶段2：NL2SQL 智能问数（三层召回 + 自纠错）
 * 阶段3：Agent 运行时（意图路由/工具系统/多轮会话）
 */
@SpringBootApplication
@MapperScan("com.power.ai.**.mapper")
@EnableScheduling // 启用定时任务（0-5 月度考核跑批）
public class PowerAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerAiApplication.class, args);
    }
}
