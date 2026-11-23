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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 知识入库（阶段 1-4）：全量重灌 + 增量同步。
 *
 * 背景：Spring AI 只提供 VectorStore 存储接口（add/delete/search）和 ETL 管道抽象，
 * 没有 LlamaIndex DocumentStore 那样的"文档级状态管理"——哪些文档已入库、
 * 当前版本是什么，必须自研。这里用一张指纹登记表补上这块拼图。
 *
 * 两种入库模式：
 * 1. 全量重灌 ingestAll()：清空全部块 → 全目录入库 → 重建登记表。
 *    用于 reindex=true 和首启（向量表为空）。
 * 2. 增量同步 ingestIncrementally()：扫描磁盘文件算 SHA-256，与登记表比对——
 *    新增 → 入库；hash 变化 → 删旧块重灌；磁盘消失 → 删块删登记；一致 → 跳过。
 *    日常重启走这条，文档没动就一个 embedding 都不调。
 *
 * 崩溃一致性：每个变更文件的处理顺序固定为
 *   删旧块 → 分块+向量化写新块 → 最后更新登记表。
 * 任一步崩溃，登记表的 hash 仍与磁盘不一致 → 下次启动自动重灌该文件，最终一致、幂等可重跑。
 *
 * 防误删：目录里一个 .md 都没有但库里有登记，极可能是 knowledge-dir 配错
 * （工作目录不对），此时拒绝同步而不是把整库清空；真清库走显式 reindex。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    /** 向量表名，与 PgVectorConfig 里 vectorTableName 保持一致 */
    private static final String VECTOR_TABLE = "rag_document";

    /** 文档指纹登记表：source 文件名 → 内容 SHA-256，增量同步的比对基准 */
    private static final String REGISTRY_TABLE = "rag_doc_registry";

    /** DashScope text-embedding-v4 批量上限：客户端断言 25、服务端实际 10，按 10 分批 */
    private static final int EMBED_BATCH_SIZE = 10;

    private final PgVectorStore pgVectorStore;
    private final PolicyDocumentSplitter splitter;
    private final RagProperties ragProperties;

    /** PG 专用连接（@Qualifier 明确语义；当前容器里只有这一个 JdbcTemplate Bean，按类型注入也唯一） */
    @Qualifier("pgJdbcTemplate")
    private final JdbcTemplate pgJdbcTemplate;

    // ==================== 全量重灌 ====================

    /**
     * 全量重灌：清空全部块和登记 → 全目录入库 → 重建登记。
     *
     * @return 入库分块数（日志战报 + 调用方校验用）
     */
    public int ingestAll() {
        long start = System.currentTimeMillis();
        ensureSchema();
        List<Path> files = scanMarkdownFiles();

        pgJdbcTemplate.update("DELETE FROM " + VECTOR_TABLE);
        pgJdbcTemplate.update("DELETE FROM " + REGISTRY_TABLE);
        if (files.isEmpty()) {
            log.warn("知识库目录下没有 .md 文档，向量库已清空，本次无数据入库");
            return 0;
        }

        int totalChunks = 0;
        for (Path file : files) {
            totalChunks += ingestFile(file);
        }

        log.info("===== 知识库全量入库完成: {} 篇文档 -> {} 个分块, 耗时 {}ms =====",
                files.size(), totalChunks, System.currentTimeMillis() - start);
        return totalChunks;
    }

    // ==================== 增量同步 ====================

    /**
     * 增量同步：磁盘文件指纹 vs 登记表，只处理新增 / 变更 / 删除的文档，其余跳过。
     * 同步复杂度 O(文件数)，与库规模无关——万级文档的知识库也只重灌动了的那几篇。
     */
    public void ingestIncrementally() {
        long start = System.currentTimeMillis();
        ensureSchema();
        Map<String, String> diskHashes = scanAndHash();
        Map<String, String> dbHashes = loadRegistryHashes();

        // 防误删：磁盘空但库里有登记 → 疑似 knowledge-dir 配错（工作目录不对），拒绝清库
        if (diskHashes.isEmpty() && !dbHashes.isEmpty()) {
            log.error("知识库目录下没有 .md 文档，但登记表已有 {} 篇入库记录——"
                    + "疑似 app.rag.knowledge-dir 配置错误，本次拒绝同步（真清库请显式设置 reindex=true）",
                    dbHashes.size());
            return;
        }

        ChangePlan plan = planChanges(diskHashes, dbHashes);
        for (String name : plan.deleted()) {
            deleteChunksOf(name);
            deleteRegistryRow(name);
        }
        // 新增与变更走同一条"删旧写新"路径：新文件删 0 行旧块，逻辑统一且幂等
        for (String name : plan.changed()) {
            reingestFile(name);
        }
        for (String name : plan.added()) {
            reingestFile(name);
        }

        log.info("===== 知识库增量同步完成: 新增 {} / 变更 {} / 删除 {} / 跳过 {}, 耗时 {}ms =====",
                plan.added().size(), plan.changed().size(), plan.deleted().size(),
                diskHashes.size() - plan.added().size() - plan.changed().size(),
                System.currentTimeMillis() - start);
    }

    /** 向量表是否为空（首启判定：为空走全量入库，非空走增量同步） */
    public boolean isEmpty() {
        Integer count = pgJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + VECTOR_TABLE, Integer.class);
        return count == null || count == 0;
    }

    // ==================== 变更检测（纯函数，可单测） ====================

    /** 变更计划：把"磁盘 vs 库"两张文件名→hash 表，归成增/改/删三类 */
    public record ChangePlan(List<String> added, List<String> changed, List<String> deleted) {
    }

    static ChangePlan planChanges(Map<String, String> diskHashes, Map<String, String> dbHashes) {
        List<String> added = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        for (Map.Entry<String, String> e : diskHashes.entrySet()) {
            String dbHash = dbHashes.get(e.getKey());
            if (dbHash == null) {
                added.add(e.getKey());
            } else if (!dbHash.equals(e.getValue())) {
                changed.add(e.getKey());
            }
        }
        for (String name : dbHashes.keySet()) {
            if (!diskHashes.containsKey(name)) {
                deleted.add(name);
            }
        }
        // 排序保证处理顺序稳定可复现
        added.sort(String::compareTo);
        changed.sort(String::compareTo);
        deleted.sort(String::compareTo);
        return new ChangePlan(added, changed, deleted);
    }

    // ==================== 单文件入库 ====================

    /** 重灌单个文件：删旧块 → 分块向量化写新块 → 最后登记（顺序即崩溃一致性保证） */
    private void reingestFile(String fileName) {
        String content = read(Path.of(ragProperties.getKnowledgeDir(), fileName));
        deleteChunksOf(fileName);
        List<Document> chunks = splitter.split(fileName, content);
        addInBatches(chunks);
        upsertRegistry(fileName, sha256(content), chunks.size());
        log.info("  已入库: {} ({} 块)", fileName, chunks.size());
    }

    /** 全量路径的单文件入库（登记表已清空，直接写 + 登记） */
    private int ingestFile(Path file) {
        String fileName = file.getFileName().toString();
        String content = read(file);
        List<Document> chunks = splitter.split(fileName, content);
        addInBatches(chunks);
        upsertRegistry(fileName, sha256(content), chunks.size());
        return chunks.size();
    }

    /** 分批写入：text-embedding-v4 服务端单次请求最多 10 条，超出 400 */
    private void addInBatches(List<Document> chunks) {
        for (int i = 0; i < chunks.size(); i += EMBED_BATCH_SIZE) {
            pgVectorStore.add(chunks.subList(i, Math.min(i + EMBED_BATCH_SIZE, chunks.size())));
        }
    }

    // ==================== 登记表与块清理 ====================

    /**
     * 建表（幂等）：登记表 + 按 source 删块的表达式索引。
     * 索引是"规模"的关键：删一个文件的块时走 metadata->>'source' 过滤，
     * 没有索引万级 chunk 会全表扫描。
     */
    private void ensureSchema() {
        pgJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + REGISTRY_TABLE + " ("
                + "source TEXT PRIMARY KEY,"
                + "file_hash TEXT NOT NULL,"
                + "chunk_count INT NOT NULL,"
                + "ingested_at TIMESTAMP NOT NULL DEFAULT now())");
        pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rag_document_source "
                + "ON " + VECTOR_TABLE + " ((metadata->>'source'))");
    }

    private Map<String, String> loadRegistryHashes() {
        return pgJdbcTemplate.query("SELECT source, file_hash FROM " + REGISTRY_TABLE, rs -> {
            Map<String, String> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
            return map;
        });
    }

    private void deleteChunksOf(String fileName) {
        pgJdbcTemplate.update("DELETE FROM " + VECTOR_TABLE + " WHERE metadata->>'source' = ?", fileName);
    }

    private void upsertRegistry(String fileName, String hash, int chunkCount) {
        pgJdbcTemplate.update("INSERT INTO " + REGISTRY_TABLE + " (source, file_hash, chunk_count) VALUES (?, ?, ?) "
                + "ON CONFLICT (source) DO UPDATE SET file_hash = EXCLUDED.file_hash, "
                + "chunk_count = EXCLUDED.chunk_count, ingested_at = now()", fileName, hash, chunkCount);
    }

    private void deleteRegistryRow(String fileName) {
        pgJdbcTemplate.update("DELETE FROM " + REGISTRY_TABLE + " WHERE source = ?", fileName);
    }

    // ==================== 文件扫描与哈希 ====================

    /** 扫描知识目录，返回 文件名 → SHA-256（有序 TreeMap 保证处理顺序稳定） */
    private Map<String, String> scanAndHash() {
        Map<String, String> map = new java.util.TreeMap<>();
        for (Path file : scanMarkdownFiles()) {
            map.put(file.getFileName().toString(), sha256(read(file)));
        }
        return map;
    }

    /** 目录下 .md 文件按文件名排序 */
    private List<Path> scanMarkdownFiles() {
        try (Stream<Path> stream = Files.list(Path.of(ragProperties.getKnowledgeDir()))) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("读取知识库目录失败: " + ragProperties.getKnowledgeDir(), e);
        }
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取知识文档失败: " + file, e);
        }
    }

    /** SHA-256 内容指纹（UTF-8 字节级），决定一个文件是否需要重灌 */
    static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", e);
        }
    }
}
