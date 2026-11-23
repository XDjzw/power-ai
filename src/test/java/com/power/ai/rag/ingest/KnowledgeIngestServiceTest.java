package com.power.ai.rag.ingest;

import com.power.ai.rag.ingest.KnowledgeIngestService.ChangePlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 增量入库的变更检测逻辑单测（不依赖 Spring 上下文 / PG / embedding）。
 * 只测 planChanges 纯函数 + sha256 指纹，增删改全走内存 Map 模拟。
 */
class KnowledgeIngestServiceTest {

    @Test
    void 新增文件_入库登记表没有_归入added() {
        ChangePlan plan = KnowledgeIngestService.planChanges(
                Map.of("A.md", "h1", "B.md", "h2"),
                Map.of("A.md", "h1"));

        assertEquals(List.of("B.md"), plan.added());
        assertTrue(plan.changed().isEmpty());
        assertTrue(plan.deleted().isEmpty());
    }

    @Test
    void 内容变化_hash不同_归入changed() {
        ChangePlan plan = KnowledgeIngestService.planChanges(
                Map.of("A.md", "h1-new"),
                Map.of("A.md", "h1-old"));

        assertEquals(List.of("A.md"), plan.changed());
        assertTrue(plan.added().isEmpty());
        assertTrue(plan.deleted().isEmpty());
    }

    @Test
    void 磁盘文件消失_归入deleted() {
        ChangePlan plan = KnowledgeIngestService.planChanges(
                Map.of("A.md", "h1"),
                Map.of("A.md", "h1", "B.md", "h2"));

        assertEquals(List.of("B.md"), plan.deleted());
        assertTrue(plan.added().isEmpty());
        assertTrue(plan.changed().isEmpty());
    }

    @Test
    void 全部一致_三类全空_日常重启零动作() {
        ChangePlan plan = KnowledgeIngestService.planChanges(
                Map.of("A.md", "h1", "B.md", "h2"),
                Map.of("B.md", "h2", "A.md", "h1"));

        assertTrue(plan.added().isEmpty());
        assertTrue(plan.changed().isEmpty());
        assertTrue(plan.deleted().isEmpty());
    }

    @Test
    void 结果有序_处理顺序稳定可复现() {
        ChangePlan plan = KnowledgeIngestService.planChanges(
                Map.of("C.md", "h", "A.md", "h", "B.md", "h-new", "D.md", "h-new2"),
                Map.of("B.md", "h-old", "D.md", "h-old2", "X.md", "h"));

        assertEquals(List.of("A.md", "C.md"), plan.added());
        assertEquals(List.of("B.md", "D.md"), plan.changed());
        assertEquals(List.of("X.md"), plan.deleted());
    }

    @Test
    void sha256_相同内容指纹相同_不同内容指纹不同() {
        String h1 = KnowledgeIngestService.sha256("hello 知识库");
        String h2 = KnowledgeIngestService.sha256("hello 知识库");
        String h3 = KnowledgeIngestService.sha256("hello 知识库2");

        assertEquals(h1, h2, "相同内容必须同指纹");
        assertFalse(h1.equals(h3), "不同内容必须不同指纹");
    }
}
