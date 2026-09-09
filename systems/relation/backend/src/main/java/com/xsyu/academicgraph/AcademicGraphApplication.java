package com.xsyu.academicgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 学术关系知识图谱构建平台 —— Spring Boot 启动入口。
 * 通过 Starter 自动装配三大存储/安全能力：
 *  - Spring Data JPA  → MySQL（权威数据源：用户/角色/学术实体主数据）
 *  - Spring Data Neo4j → Neo4j（可重建的图投影，存放图谱节点与关系）
 *  - Spring Security   → Session + CSRF 认证（不使用 JWT）
 * @EnableScheduling 启用定时任务：GraphSyncService 每 5 秒轮询 Outbox 事件投影到 Neo4j。
 * @ConfigurationPropertiesScan 扫描 app.openalex / app.llm 等配置类（infrastructure 包下）。
 * 分层约定：api（Controller/DTO）→ application（业务编排）→ domain（实体/仓储）→ infrastructure（安全/错误/投影）。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AcademicGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcademicGraphApplication.class, args);
    }

}
