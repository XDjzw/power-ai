package com.power.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 知识库配置（app.rag.*，阶段 1-4 知识入库用）。
 *
 * 与 PgVectorConfig 的分工：那边管"连到哪、表长什么样"，这边管"灌什么数据、什么策略"。
 */
@Configuration
public class RagConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.rag")
    public RagProperties ragProperties() {
        return new RagProperties();
    }

    @Data
    public static class RagProperties {

        /** 知识库文档目录（.md 文件），相对项目根目录 */
        private String knowledgeDir = "docs/knowledge";

        /** 是否强制重灌：true 时启动必全量重灌；false 时仅向量表为空才入库 */
        private boolean reindex = false;
    }
}
