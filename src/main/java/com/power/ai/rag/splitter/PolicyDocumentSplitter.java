package com.power.ai.rag.splitter;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 政策文档分块器（自研，阶段 1-3）。
 *
 * 设计：知识库文档是"政策条文"型结构，按 Markdown 标题层级分块：
 * - 每个 ## 小节一个块；文档标题（#）与标题下的引言单独成块
 * - ### 子小节不拆块，保留在小节块内（条款上下文完整）；子小节标题记入标题链
 * - 超长小节按段落二次切分（贪婪装箱，段落不拆散），保证单块不超过 MAX_CHUNK_CHARS
 * - 每块正文前拼标题链（如"月度计划考核规则 > 判定规则"），向量化时标题语义与正文一起编码，
 *   检索时才能靠标题词命中条款所在章节
 * - metadata 记录 source / title / section / titleChain / chunkIndex，
 *   生成答案时按标题链给出引用出处
 *
 * 注意：单段超过 MAX_CHUNK_CHARS 时（如大表格）不做硬拆，整段保留——切碎表格比超长更糟。
 *
 * 无状态纯函数组件：分块只依赖入参，不依赖 Spring 上下文，
 * 单元测试可直接 new；注册成 Bean 仅为了让 KnowledgeIngestService 注入。
 */
@Component
public class PolicyDocumentSplitter {

    /** 单块最大字符数，超过则按段落二次切分 */
    private static final int MAX_CHUNK_CHARS = 600;

    /** 标题链分隔符 */
    public static final String CHAIN_SEPARATOR = " > ";

    /**
     * 把一篇知识文档切成若干块。
     *
     * @param fileName 来源文件名（写入 metadata.source）
     * @param content  文档全文
     * @return 有序块列表，每块是一段可独立向量化的文本
     */
    public List<Document> split(String fileName, String content) {
        List<Document> chunks = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        String h1 = null, h2 = null, h3 = null;
        StringBuilder body = new StringBuilder();
        int chunkSeq = 0;

        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("### ")) {
                h3 = t.substring(4).trim();
            } else if (t.startsWith("## ") || t.startsWith("# ")) {
                chunkSeq = flush(fileName, h1, h2, h3, body, chunks, chunkSeq);
                if (t.startsWith("## ")) {
                    h2 = t.substring(3).trim();
                    h3 = null;
                } else {
                    h1 = t.substring(2).trim();
                    h2 = null;
                    h3 = null;
                }
            } else if (!t.isEmpty()) {
                body.append(t).append('\n');
            } else if (body.length() > 0) {
                // 保留空行作为段落分隔（splitLong 按空行切）
                body.append('\n');
            }
        }
        flush(fileName, h1, h2, h3, body, chunks, chunkSeq);
        return chunks;
    }

    /** 把当前累积的正文落成一个或多个块 */
    private int flush(String fileName, String h1, String h2, String h3,
                      StringBuilder body, List<Document> out, int seq) {
        String text = body.toString().trim();
        body.setLength(0);
        if (text.isEmpty()) {
            return seq;
        }
        String chain = chainOf(h1, h2, h3);
        for (String part : splitLong(text, MAX_CHUNK_CHARS)) {
            String chunkText = chain.isEmpty() ? part : chain + "\n" + part;
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", fileName);
            metadata.put("title", h1 == null ? "" : h1);
            metadata.put("section", h2 == null ? "" : h2);
            metadata.put("subsection", h3 == null ? "" : h3);
            metadata.put("titleChain", chain);
            metadata.put("chunkIndex", seq);
            out.add(new Document(chunkText, metadata));
            seq++;
        }
        return seq;
    }

    private String chainOf(String h1, String h2, String h3) {
        List<String> parts = new ArrayList<>();
        for (String s : new String[]{h1, h2, h3}) {
            if (s != null && !s.isBlank()) {
                parts.add(s.trim());
            }
        }
        return String.join(CHAIN_SEPARATOR, parts);
    }

    /** 超长文本按空行切段后贪婪装箱：段不拆散，凑满一箱即封块 */
    private List<String> splitLong(String text, int max) {
        if (text.length() <= max) {
            return List.of(text);
        }
        List<String> paras = Arrays.stream(text.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            if (cur.length() > 0 && cur.length() + p.length() + 1 > max) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append('\n');
            }
            cur.append(p);
        }
        if (cur.length() > 0) {
            out.add(cur.toString().trim());
        }
        return out;
    }
}
