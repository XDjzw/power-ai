package com.power.ai.rag.ingest;

import com.power.ai.config.RagConfig.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时自动入库（阶段 1-4）。
 *
 * 时机：ApplicationRunner 在所有 Bean 初始化完成后执行，
 * 此时 PgVectorStore 已建好表（initializeSchema），入库不会撞上"表不存在"。
 *
 * 三分支策略：
 * 1. app.rag.reindex=true → 强制全量重灌（改知识库后想整体刷新用）；
 * 2. 向量表为空（首启/清库后）→ 全量入库；
 * 3. 表非空 → 增量同步：按文件 SHA-256 指纹比对，只重灌新增/变更的文档，
 *    删除磁盘上已消失的文档，其余一个 embedding 都不调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIngestRunner implements ApplicationRunner {

    private final KnowledgeIngestService ingestService;
    private final RagProperties ragProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (ragProperties.isReindex()) {
            log.info("app.rag.reindex=true，强制全量重灌知识库");
            ingestService.ingestAll();
            return;
        }
        if (ingestService.isEmpty()) {
            ingestService.ingestAll();
        } else {
            ingestService.ingestIncrementally();
        }
    }
}
