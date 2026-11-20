package com.power.ai.common.seeder;

import cn.hutool.crypto.digest.BCrypt;
import com.power.ai.biz.auth.entity.SysUser;
import com.power.ai.biz.auth.service.SysUserService;
import com.power.ai.biz.order.entity.FaultType;
import com.power.ai.biz.order.entity.MaintenanceOrder;
import com.power.ai.biz.order.service.OrderService;
import com.power.ai.biz.stats.entity.GenDailyStats;
import com.power.ai.biz.stats.service.StatsService;
import com.power.ai.biz.station.entity.PowerStation;
import com.power.ai.biz.station.service.StationService;
import com.power.ai.common.security.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 演示数据初始化器（仅开发环境启用）。
 *
 * 设计要点：
 * 1. CommandLineRunner：Spring 容器完全就绪后执行，比 @PostConstruct 更适合做
 *    依赖多个 Bean 的初始化
 * 2. @ConditionalOnProperty：由配置项 app.seeder.enabled 控制，生产环境不配置就永远不执行
 * 3. 幂等：表里已有数据就跳过，重启不会重复灌数
 * 4. 固定随机种子：每次初始化出的数据完全一致，演示效果可复现
 * 5. 刻意埋了三种业务现象，是后面 NL2SQL 智能问数的"考题素材"：
 *    - 华锐二期(WF002)：计划定得高、实际产能差 → 常年欠进度（阶段0-5 考核跑批的 BEHIND 案例）
 *    - 北疆风电场(WF003)：冬季限电严重(8%~25%) → "哪个电站限电损失最大"的答案
 *    - 云顶风电场(WF005)：故障率偏高 → "故障损失电量"的答案
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final StationService stationService;
    private final StatsService statsService;
    private final OrderService orderService;
    private final SysUserService sysUserService;

    /** 固定种子：每次灌的数据一模一样，排查问题/演示效果都可复现 */
    private static final long RANDOM_SEED = 42L;
    /** 造两年日统计数据 */
    private static final int SEED_DAYS = 730;
    /** 分批入库大小，避免一次性攒 5800 条对象在内存里 */
    private static final int BATCH_SIZE = 500;

    @Override
    public void run(String... args) {
        if (stationService.count() > 0) {
            log.info("演示数据已存在，跳过初始化");
            return;
        }
        log.info("===== 开始初始化演示数据 =====");
        long start = System.currentTimeMillis();

        List<PowerStation> stations = seedStations();
        seedDailyStats(stations);
        seedOrders(stations);
        seedUsers(stations);

        log.info("===== 演示数据初始化完成，耗时 {}ms =====", System.currentTimeMillis() - start);
    }

    /** 9 个电站：5 个已并网风电场 + 3 个光伏电站 + 1 个在建风电场 */
    private List<PowerStation> seedStations() {
        LocalDateTime now = LocalDateTime.now();
        List<PowerStation> stations = List.of(
                station("WF001", "华锐风电场一期", "WIND", 100000, "华东分公司", "王建国", "2019-06-30", "ONLINE", now),
                station("WF002", "华锐风电场二期", "WIND", 50000, "华东分公司", "王建国", "2021-12-15", "ONLINE", now),
                station("WF003", "北疆风电场", "WIND", 200000, "华北分公司", "李振华", "2018-09-20", "ONLINE", now),
                station("WF004", "滨海风电场", "WIND", 150000, "华东分公司", "赵海燕", "2022-03-10", "ONLINE", now),
                station("WF005", "云顶风电场", "WIND", 80000, "西南分公司", "陈志强", "2020-11-05", "ONLINE", now),
                station("PV001", "阳光光伏电站", "SOLAR", 120000, "西北分公司", "刘芳", "2019-06-28", "ONLINE", now),
                station("PV002", "戈壁光伏电站", "SOLAR", 200000, "西北分公司", "刘芳", "2021-07-01", "ONLINE", now),
                station("PV003", "绿洲光伏电站", "SOLAR", 60000, "华北分公司", "张伟", "2023-04-18", "ONLINE", now),
                station("WF006", "深海风电场", "WIND", 100000, "华南分公司", "孙立", "2026-12-31", "BUILDING", now)
        );
        stationService.saveBatch(stations);
        log.info("电站 {} 个入库", stations.size());
        return stations;
    }

    private PowerStation station(String stationNo, String name, String stationType,
                                 double capacityKw, String regionCompany, String managerName,
                                 String gridConnectDate, String status, LocalDateTime now) {
        PowerStation s = new PowerStation();
        s.setStationNo(stationNo);
        s.setName(name);
        s.setStationType(stationType);
        s.setCapacityKw(BigDecimal.valueOf(capacityKw));
        s.setRegionCompany(regionCompany);
        s.setManagerName(managerName);
        s.setGridConnectDate(LocalDate.parse(gridConnectDate));
        s.setStatus(status);
        s.setCreatedAt(now);
        return s;
    }

    /** 每个已并网电站造两年日统计，共 8 × 730 = 5840 条，分批入库 */
    private void seedDailyStats(List<PowerStation> stations) {
        Random random = new Random(RANDOM_SEED);
        LocalDate today = LocalDate.now();
        List<GenDailyStats> batch = new ArrayList<>();
        int total = 0;

        for (PowerStation station : stations) {
            if (!"ONLINE".equals(station.getStatus())) {
                continue; // 在建电站没有日统计数据
            }
            for (int i = 0; i < SEED_DAYS; i++) {
                batch.add(buildDailyStats(random, station, today.minusDays(SEED_DAYS - i)));
                if (batch.size() >= BATCH_SIZE) {
                    statsService.saveBatch(batch);
                    total += batch.size();
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            statsService.saveBatch(batch);
            total += batch.size();
        }
        log.info("日统计数据 {} 条入库", total);
    }

    /** 按真实业务规律模拟一天的发电数据 */
    private GenDailyStats buildDailyStats(Random random, PowerStation station, LocalDate date) {
        int month = date.getMonthValue();
        boolean isWind = "WIND".equals(station.getStationType());
        double capacity = station.getCapacityKw().doubleValue();

        // 1. 季节因子：风电冬高夏低（大风季），光伏夏高冬低（日照）
        double season;
        if (isWind) {
            season = (month >= 11 || month <= 3) ? 0.32 : 0.18;
        } else {
            season = (month >= 5 && month <= 8) ? 0.22 : 0.10;
        }
        double weatherNoise = 0.6 + random.nextDouble() * 0.8; // 0.6~1.4 天气扰动
        double dayGen = capacity * 24 * season * weatherNoise;

        // 华锐二期：设备老旧、风资源条件差，实际产能只有正常水平的 35% → 常年欠进度的素材
        if ("WF002".equals(station.getStationNo())) {
            dayGen *= 0.35;
        }

        // 2. 故障损失：云顶故障率偏高(1.5%的天数)，其他电站偶发(0.2%)。
        //    故障当天实际发电量同步下降，损失电量单独记录
        double faultLoss = 0;
        double faultRate = "WF005".equals(station.getStationNo()) ? 0.015 : 0.002;
        if (random.nextDouble() < faultRate) {
            double ratio = 0.3 + random.nextDouble() * 0.4; // 损失当日 30%~70% 电量
            faultLoss = dayGen * ratio;
            dayGen *= (1 - ratio);
        }

        // 3. 厂用电指标：厂用电约 4%，上网电量 = 发电 - 厂用电
        double consumption = dayGen * 0.04;
        double netPower = dayGen - consumption;
        double outputPower = netPower * 0.97;
        double auxRate = consumption / dayGen * 100;

        // 4. 限电：北疆冬季电网消纳不足，限电 8%~25%；其他电站偶发小限电(5%的天数)
        double curtailRatio = 0;
        if ("WF003".equals(station.getStationNo()) && (month >= 11 || month <= 3)) {
            curtailRatio = 8 + random.nextDouble() * 17;
        } else if (random.nextDouble() < 0.05) {
            curtailRatio = 0.5 + random.nextDouble() * 2.5;
        }
        double curtailLoss = dayGen * curtailRatio / 100;

        // 5. 月计划电量（同月每天都存同一个值）。
        //    华锐二期计划因子 0.30 但实际产能只有 0.30×0.35≈0.10 → 发电进度永远跟不上时间进度，
        //    阶段0-5 考核跑批会把它判为 BEHIND
        double planFactor;
        if ("WF002".equals(station.getStationNo())) {
            planFactor = 0.30;
        } else if (isWind) {
            planFactor = 0.24;
        } else {
            planFactor = 0.16;
        }
        double monthPlan = capacity * 24 * 30 * planFactor;

        // 6. 预测值与利用小时数
        double productionFc = dayGen * (0.9 + random.nextDouble() * 0.2); // 预测值围绕实际值±10%
        BigDecimal windSpeedFc = null;
        if (isWind) {
            windSpeedFc = bd(2 + random.nextDouble() * 13, 2); // 预测风速 2~15 m/s
        }
        double utilHours = dayGen / capacity;

        GenDailyStats s = new GenDailyStats();
        s.setStationId(station.getId());
        s.setStatDate(date);
        s.setDayGenPower(bd(dayGen, 5));
        s.setDayNetPower(bd(netPower, 5));
        s.setDayOutputPower(bd(outputPower, 5));
        s.setPlantConsumption(bd(consumption, 2));
        s.setAuxiliaryPowerRate(bd(auxRate, 5));
        s.setCurtailmentLoss(bd(curtailLoss, 2));
        s.setCurtailmentRatio(bd(curtailRatio, 5));
        s.setFaultLoss(bd(faultLoss, 2));
        s.setMonthPlanPower(bd(monthPlan, 2));
        s.setProductionFc(bd(productionFc, 2));
        s.setWindSpeedFc(windSpeedFc);
        s.setAvgUtilHours(bd(utilHours, 2));
        return s;
    }

    /** 造 3 张工单覆盖三种状态。刻意调用真实业务方法而不是直接 save，
     *  让造数过程也走状态机，和线上行为保持一致 */
    private void seedOrders(List<PowerStation> stations) {
        // 1 张处理中：云顶风电场齿轮箱故障
        MaintenanceOrder o1 = new MaintenanceOrder();
        o1.setStationId(stations.get(4).getId());
        o1.setDeviceName("7号风机齿轮箱");
        o1.setFaultType(FaultType.GEARBOX);
        o1.setDescription("齿轮箱高速轴振动超标，需要停机检修");
        o1 = orderService.createOrder(o1);
        orderService.accept(o1.getId(), "王海涛");

        // 1 张已完成：北疆风电场集电线路跳闸
        MaintenanceOrder o2 = new MaintenanceOrder();
        o2.setStationId(stations.get(2).getId());
        o2.setDeviceName("3号集电线路");
        o2.setFaultType(FaultType.LINE);
        o2.setDescription("大风天气集电线路跳闸，重合闸失败");
        o2 = orderService.createOrder(o2);
        orderService.accept(o2.getId(), "刘明");
        orderService.finish(o2.getId(), "已巡线处理并恢复送电");

        // 1 张待接单：华锐一期变桨系统
        MaintenanceOrder o3 = new MaintenanceOrder();
        o3.setStationId(stations.get(0).getId());
        o3.setDeviceName("12号风机变桨系统");
        o3.setFaultType(FaultType.PITCH);
        o3.setDescription("变桨角度偏差超限报警");
        orderService.createOrder(o3);

        log.info("工单 3 条入库（走真实业务方法，状态机生效）");
    }

    /** double → BigDecimal，精度按企业表规格 */
    private BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 造 3 个三角色用户（0-6 JWT 登录用），密码统一 123456。
     * 单独做幂等判断而不是挂在 station 的判断上：用户表独立演进，
     * 以后单独补用户时不用动其他数据。
     */
    private void seedUsers(List<PowerStation> stations) {
        if (sysUserService.count() > 0) {
            log.info("系统用户已存在，跳过初始化");
            return;
        }
        // manager 绑定华锐一期（WF001），与其站长"王建国"一致——阶段3 "我的工单"的演示素材
        List<SysUser> users = List.of(
                user("admin", "系统管理员", UserRole.ADMIN, null),
                user("manager", "王建国", UserRole.MANAGER, stations.get(0).getId()),
                user("operator", "运营专员", UserRole.OPERATOR, null)
        );
        sysUserService.saveBatch(users);
        log.info("系统用户 {} 个入库（密码统一 123456，BCrypt 哈希存储）", users.size());
    }

    private SysUser user(String username, String realName, UserRole role, Long stationId) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(BCrypt.hashpw("123456"));
        u.setRealName(realName);
        u.setRole(role);
        u.setStationId(stationId);
        return u;
    }
}
