# 电力智能服务平台（power-ai）开发计划

> 新能源发电运营平台 + AI Agent 层：传统 Java 底座 + 知识问答（RAG）+ 智能问数（NL2SQL）
> 更新日期：2026-11-23

## 阶段总览

| # | 阶段 | 内容 | 状态 |
|---|------|------|------|
| 0-1 | 项目骨架 | pom / 配置 / DDL / 启动类 | ✅ |
| 0-2 | common 层 | Result / 全局异常 / traceId / MDC 日志 | ✅ |
| 0-3 | CRUD + 造数 | 电站 / 日统计 / 工单 CRUD + DataSeeder | ✅ |
| 0-4 | 工单 + MQ | 状态机 + RabbitMQ 通知 / 超时升级 | ✅ |
| 0-5 | 考核跑批 | 月计划进度考核 + 欠进度告警 | ✅ |
| 0-6 | JWT 权限 | 三角色登录鉴权 | ✅ |
| 1 | RAG 知识问答 | 新能源知识库 + 双路召回 + 引用溯源 | 🚧 |
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

链路：条款分块 → embedding → pgvector → **双路召回**（向量 + 自研 BM25）→ **RRF 融合** → qwen-plus 生成**带引用**答案。
自研组件：`PolicyDocumentSplitter` / `Bm25Index` / `RrfFusion`。
模型方案：百炼 DashScope（qwen-plus 对话 + text-embedding-v4 向量化 1024 维），检索与存储全部自研自管（不用平台托管知识库）。

### 1-1 知识库整理 ✅
- `docs/knowledge/` 6 篇条款化 Markdown：
  - 平台规则类 4 篇（从系统真实逻辑提炼，问答与系统行为一致）：月度计划考核规则 / 限电与弃风 / 厂用电与电量口径 / 设备故障与工单管理
  - 行业知识类 2 篇（整理自权威公开来源，非自编）：设备结构与故障处置（二十五项反措 2023 版 + FMEA 文献 + 光伏运维统计）/ 新能源发电基础与消纳（贝茨极限 + 807 号消纳机制文件 + 弃风弃光公开数据）
- 每篇带"适用范围"与来源标注，生成答案的引用可溯源

### 1-2 基础设施 ✅
- PostgreSQL 17.12 + pgvector 0.8.7：Docker 路线（WSL2 dockerd，容器 power-pg 映射 **5433**，
  5432 被电商助手项目占用）；conda 路线因旧版求解器解不动依赖已放弃
- pom 引入 spring-ai-bom 1.1.8 + dashscope starter 1.1.2.3 + pgvector starter
- `PgVectorConfig`：PG 独立 JdbcTemplate + PgVectorStore（业务 MySQL 主数据源不动，
  PG 连接不注册 DataSource Bean，否则 DataSourceAutoConfiguration 退让导致业务层查错库）
- 启动自动建表 `rag_document`（uuid / content / metadata json / embedding vector(1024) + HNSW 余弦索引）

### 1-3 PolicyDocumentSplitter 分块器（自研）✅
- 按 Markdown 标题层级分块：每个 `##` 小节一个块，`###` 子小节并入不拆；块元数据带完整标题链（source/title/section/subsection/titleChain/chunkIndex）
- 标题链前置拼进正文参与向量编码（Contextual Retrieval 的零成本替代方案）；超长小节（>600 字符）按段落贪婪装箱二次切分，表格不硬切
- 单元测试直接对 6 篇真实文档验证：35 块、无超长块、标题链完整

### 1-4 知识入库 ✅
- `KnowledgeIngestService`：扫描 docs/knowledge/ → 分块 → PgVectorStore.add（内部自动向量化）；重灌前清表，幂等可重跑
- `KnowledgeIngestRunner`：启动自动入库——reindex=true 强制重灌，否则仅表空时入库
- 踩坑：text-embedding-v4 批量上限，客户端断言 25 条、服务端实际 10 条（400），按 10 分批写入
- 验证：35 块落库、全部 1024 维、metadata 标题链完整

### 1-5 双路召回 ⬜
- 向量召回：pgvector HNSW 余弦相似度 top-K
- 关键词召回：自研 `Bm25Index`（内存索引，分块后建，启动时加载）

### 1-6 RRF 融合 + 带引用生成 ⬜
- 自研 `RrfFusion` 融合两路结果（RRF 公式，k=60）
- qwen-plus 生成：prompt 附带召回条款 + 标题链，答案末尾列引用出处
- REST 接口 `/api/rag/ask`（阶段 3 再接 SSE 流式）

### 1-7 评测 ⬜
- 50-100 问测试集（覆盖四篇文档，含"系统真实数据"类问题）
- 单路 vs 双路召回命中率对比 + 生成答案引用正确率 → 简历量化指标

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
- [x] PostgreSQL + pgvector 安装（Docker 路线：WSL dockerd + power-pg 容器，端口 5433）
