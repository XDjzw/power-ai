# 电力智能服务平台（power-ai）开发计划

> 新能源发电运营平台 + AI Agent 层：传统 Java 底座 + 知识问答（RAG）+ 智能问数（NL2SQL）
> 更新日期：2026-11-19

## 阶段总览

| # | 阶段 | 内容 | 状态 |
|---|------|------|------|
| 0-1 | 项目骨架 | pom / 配置 / DDL / 启动类 | ✅ |
| 0-2 | common 层 | Result / 全局异常 / traceId / MDC 日志 | ✅ |
| 0-3 | CRUD + 造数 | 电站 / 日统计 / 工单 CRUD + DataSeeder | ✅ |
| 0-4 | 工单 + MQ | 状态机 + RabbitMQ 通知 / 超时升级 | ✅ |
| 0-5 | 考核跑批 | 月计划进度考核 + 欠进度告警 | ✅ |
| 0-6 | JWT 权限 | 三角色登录鉴权 | ✅ |
| 1 | RAG 知识问答 | 新能源知识库 + 双路召回 + 引用溯源 | ⬜ |
| 2 | NL2SQL 智能问数 | 三层召回 + 自纠错 loop + SQL 安全 | ⬜ |
| 3 | Agent 运行时 | 意图路由 / 工具系统 / 多轮会话 / SSE | ⬜ |
| 4 | 评测打磨 | 测试集 / 消融数据 / 缓存 / 简历指标 | ⬜ |

---

## 阶段 0：业务底座（新能源发电运营域）

### 0-3 CRUD + DataSeeder
- `power_station` / `gen_daily_stats` / `maintenance_order` 三个模块四层结构
- DataSeeder 造数要求：8-10 个电站（风/光混合）、约 2 年日数据、故意埋业务现象（欠进度、高限电、故障损失）——阶段 2 问数演示的素材
- 教学方式：我示范 station 模块 → 自己照模式敲 gen_daily_stats

### 0-4 工单 + RabbitMQ
- 状态机：WAITING → PROCESSING → DONE / TIMEOUT → ESCALATED
- 创建工单 → MQ 通知电站负责人；TTL 2 小时 + 死信队列 → 超时自动升级
- 可靠性三件套：生产者 confirm / 消费者手动 ack / 幂等（工单号 + 状态前置校验）
- 前置：装 RabbitMQ（机器上还没有，原生或 Docker 待定）

### 0-5 月计划进度考核跑批
- 每天凌晨 1 点 `@Scheduled`：聚合各电站本月累计发电量 vs 月计划电量
- 判定：发电进度 < 时间进度 × 0.9 → 欠进度（BEHIND）
- 结果 UPSERT 到 `month_assessment`（唯一键 station_id + assess_month，幂等）
- 欠进度电站 → RabbitMQ 告警通知运营
- Redisson 分布式锁防重复跑批

### 0-6 JWT
- `sys_user` 表，三角色：管理员 / 电站负责人 / 运营
- 登录 + 拦截器 + 角色鉴权；为阶段 3 "我的工单"身份绑定铺路

---

## 阶段 1：RAG 知识问答

- 知识库：新能源业务文档（限电/弃风政策、厂用电率定义、计划考核规则、设备故障术语）整理成 Markdown
- 链路：条款分块 → embedding → pgvector → **双路召回**（向量 + 自研 BM25/HanLP）→ **RRF 融合** → ChatClient 生成**带引用**答案
- 自研组件：`PolicyDocumentSplitter` / `Bm25Index` / `RrfFusion`
- 前置：装 PostgreSQL + pgvector
- 评测：50-100 问测试集，单路 vs 双路召回准确率对比

## 阶段 2：NL2SQL 智能问数

- 三层召回 Java 移植（示例 / 列 / 值）——schema 注释里的业务同义词是列召回的命根子
- 链路：召回 → LLM 生成 SQL → JSqlParser 语法树拦截（只许 SELECT）→ 只读执行 → 结果校验失败带错误信息重写，**自纠错 ≤ 3 轮**
- 多轮指代消解："那上个月呢？"
- 自研组件：`ExampleRecall` / `ColumnRecall` / `ValueRecall` / `Nl2sqlAgent` / `SqlGuard`
- 评测：黄金 SQL 集 + 消融（有无 few-shot / 有无自纠错）

## 阶段 3：Agent 运行时

- 意图路由（LLM 分类）：知识问答 / 数据问询 / 业务操作
- 工具注册表：知识检索 / SQL 执行 / 工单查询
- 多轮会话（Redis）+ 身份绑定 + SSE 过程可视化 + 每步 token 成本统计
- Vue 聊天界面（管理页同期完成）

## 阶段 4：评测打磨

- 两套测试集指标 + 消融数据 → 简历量化指标的唯一来源
- Redis 高频问答缓存、请求限流

---

## 面试故事线

> 在新能源发电运营平台上构建 AI Agent 层：统一意图路由，知识问答走双路召回 RAG（Embedding + 自研 BM25，RRF 融合），数据问询走三层召回 NL2SQL（示例/列/值）+ 自纠错 loop，支持工单工具调用、多轮对话和全链路成本统计。

## 待办依赖

- [x] RabbitMQ 安装（原生安装 D:\rabbitmq 4.1.4，管理台 15672 已开启）
- [ ] PostgreSQL + pgvector 安装（阶段 1 前）
