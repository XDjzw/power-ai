package com.power.ai.rag.ingest;

import com.power.ai.config.RagConfig.RagProperties;
import com.power.ai.rag.splitter.PolicyDocumentSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 知识入库（阶段 1-4）。
 *
 * 链路：扫描知识目录 .md → PolicyDocumentSplitter 分块 → PgVectorStore.add
 * （add 内部自动调 EmbeddingModel 把每块向量化后写入 pgvector，单块一行）。
 * 幂等设计：重灌前先清空 rag_document 表，无论启动几次、跑几次结果一致。
 *
 * 清表用原生 SQL 而不是 PgVectorStore.delete：delete 按 id 删，
 * 要删"全部"得先查全表 id 再删，绕一圈；这里表就是我们的私有数据，DELETE 直截了当。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    /** 向量表名，与 PgVectorConfig 里 vectorTableName 保持一致 */
    private static final String VECTOR_TABLE = "rag_document";

    private final PgVectorStore pgVectorStore;
    private final PolicyDocumentSplitter splitter;
    private final RagProperties ragProperties;

    /** PG 专用连接（@Qualifier 明确语义；当前容器里只有这一个 JdbcTemplate Bean，按类型注入也唯一） */
    @Qualifier("pgJdbcTemplate")
    private final JdbcTemplate pgJdbcTemplate;

    /**
     * 全量入库：清空向量表 → 扫描知识目录全部 .md → 分块 → 向量化写入。
     *
     * @return 入库分块数（日志战报 + 调用方校验用）
     */
    public int ingestAll() {
        long start = System.currentTimeMillis();
        List<Path> files = listMarkdownFiles(Path.of(ragProperties.getKnowledgeDir()));
        if (files.isEmpty()) {
            log.warn("知识库目录 {} 下没有 .md 文档，跳过入库", Path.of(ragProperties.getKnowledgeDir()).toAbsolutePath());
            return 0;
        }

        List<Document> allChunks = new ArrayList<>();
        for (Path file : files) {
            String content = read(file);
            allChunks.addAll(splitter.split(file.getFileName().toString(), content));
        }

        pgJdbcTemplate.update("DELETE FROM " + VECTOR_TABLE);
        // 分批写入：text-embedding-v4 的批量上限，客户端断言是 25 条，服务端实际是 10 条
        // （400: "batch size is invalid, it should not be larger than 10"）。
        // 踩坑记录：先按 25 试 → 客户端抛异常；改成 20 → 客户端过了，服务端 400。
        // 外部 API 的批量上限必须自己分片，不能指望框架兜底，客户端校验甚至可能比服务端松。
        final int batchSize = 10;
        for (int i = 0; i < allChunks.size(); i += batchSize) {
            pgVectorStore.add(allChunks.subList(i, Math.min(i + batchSize, allChunks.size())));
        }

        log.info("===== 知识库入库完成: {} 篇文档 -> {} 个分块, 耗时 {}ms =====",
                files.size(), allChunks.size(), System.currentTimeMillis() - start);
        return allChunks.size();
    }

    /** 向量表是否为空（启动时决定要不要自动入库） */
    public boolean isEmpty() {
        Integer count = pgJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + VECTOR_TABLE, Integer.class);
        return count == null || count == 0;
    }

    /** 目录下 .md 文件按文件名排序，保证入库顺序稳定（chunkIndex 可复现） */
    private List<Path> listMarkdownFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("读取知识库目录失败: " + dir.toAbsolutePath(), e);
        }
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取知识文档失败: " + file, e);
        }
    }
}
