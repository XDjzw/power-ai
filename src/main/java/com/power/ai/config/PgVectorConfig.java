package com.power.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量库（PostgreSQL + pgvector）独立配置。
 *
 * 背景：业务库是 MySQL（spring.datasource 主数据源，MyBatis-Plus 使用），
 * pgvector starter 的自动装配只会绑定主数据源，因此这里手动构建 PG 专用
 * JdbcTemplate + PgVectorStore Bean（自动装配检测到已有 PgVectorStore Bean 会退让）。
 *
 * 关键坑（踩过一次）：PG 连接绝不能注册成 DataSource Bean——
 * 一旦容器里有自定义 DataSource，Spring Boot 的 DataSourceAutoConfiguration
 * 会认为"数据源已存在"而退让，MySQL 主数据源就不会创建，
 * MyBatis-Plus 会抓到 PG 连接去查业务表（relation "power_station" does not exist）。
 * 所以 Hikari 池在 pgJdbcTemplate Bean 方法内部构建，不暴露给容器。
 * 业务库与向量库分离，互不影响。
 */
@Configuration
public class PgVectorConfig {

    /** app.pg.* 连接参数（application-dev.yml），端口 5433：5432 被电商助手项目的 postgres 容器占用 */
    @Bean
    @ConfigurationProperties(prefix = "app.pg")
    public PgProperties pgProperties() {
        return new PgProperties();
    }

    /**
     * PG 专用 JdbcTemplate（内含独立 Hikari 池，仅向量库使用）。
     * 连接参数先绑到 POJO 再构建：直接 @ConfigurationProperties 绑 HikariDataSource
     * 会因 yml 的 url 键对不上 setJdbcUrl setter 而绑不上。
     */
    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(PgProperties props) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(props.getUrl())
                .username(props.getUsername())
                .password(props.getPassword())
                .driverClassName(props.getDriverClassName())
                .build();
        return new JdbcTemplate(ds);
    }

    /**
     * 向量存储。initializeSchema(true)：启动时自动执行
     * CREATE EXTENSION IF NOT EXISTS vector + 建表（rag_document 表），幂等。
     * dimensions 必须与 DashScope text-embedding-v4 的输出维度（1024）一致。
     */
    @Bean
    public PgVectorStore pgVectorStore(@Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate,
                                       EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(pgJdbcTemplate, embeddingModel)
                .vectorTableName("rag_document")
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .idType(PgVectorStore.PgIdType.UUID)
                .initializeSchema(true)
                .build();
    }

    @Data
    public static class PgProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
    }
}
