package com.power.ai.rag.splitter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PolicyDocumentSplitter 单元测试（不依赖 Spring 上下文）。
 * 直接对 docs/knowledge/ 六篇真实文档做分块验证。
 */
class PolicyDocumentSplitterTest {

    private final PolicyDocumentSplitter splitter = new PolicyDocumentSplitter();

    @Test
    void 月度考核规则文档_按小节分块_元数据完整() throws IOException {
        List<Document> chunks = splitFile("月度计划考核规则.md");

        // 6 个 ## 小节 + 1 个文档引言块 = 7 块（全文无超长小节，不会二次切分）
        assertEquals(7, chunks.size());

        for (Document c : chunks) {
            assertEquals("月度计划考核规则.md", c.getMetadata().get("source"));
            assertFalse(((String) c.getMetadata().get("titleChain")).isEmpty(),
                    "每块都应带标题链");
        }

        // 判定规则小节应含 0.9 判定系数，且标题链精确到小节（保留原文序号）
        Document rule = chunks.stream()
                .filter(c -> "4. 判定规则".equals(c.getMetadata().get("section")))
                .findFirst().orElseThrow();
        assertTrue(rule.getText().contains("0.9"));
        assertEquals("月度计划考核规则 > 4. 判定规则", rule.getMetadata().get("titleChain"));
        // 正文前拼了标题链（向量化时标题语义参与编码）
        assertTrue(rule.getText().startsWith("月度计划考核规则 > 4. 判定规则"));
    }

    @Test
    void 全部知识文档可切分_无超长块_总量合理() throws IOException {
        String[] docs = {"月度计划考核规则.md", "限电与弃风.md", "厂用电与电量口径.md",
                "设备故障与工单管理.md", "设备结构与故障处置.md", "新能源发电基础与消纳.md"};
        int total = 0;
        for (String name : docs) {
            List<Document> chunks = splitFile(name);
            assertFalse(chunks.isEmpty(), name + " 分块结果为空");
            for (Document c : chunks) {
                // 正文部分 ≤ 600，拼上标题链后留 100 字符余量
                assertTrue(c.getText().length() <= 700,
                        name + " 出现超长块: " + c.getText().length());
            }
            total += chunks.size();
        }
        // 下限 30 只为防"整篇一坨"的退化情况，实测 35 块左右
        assertTrue(total >= 30, "六篇总块数应不少于 30，实际 " + total);
        System.out.println("六篇知识文档总块数: " + total);
    }

    private List<Document> splitFile(String fileName) throws IOException {
        Path path = Path.of("docs/knowledge", fileName);
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return splitter.split(fileName, content);
    }
}
