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
 * 策略：app.rag.reindex=true 强制全量重灌；否则仅当向量表为空才入库——
 * 日常重启不重复向量化（省 embedding 调用），想刷数据显式开 reindex。
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
            log.info("app.rag.reindex=true，强制重灌知识库");
            ingestService.ingestAll();
            return;
        }
        if (ingestService.isEmpty()) {
            ingestService.ingestAll();
        } else {
            log.info("向量表已有数据，跳过自动入库（如需重灌设置 app.rag.reindex=true）");
        }
    }
}
