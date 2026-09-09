package com.xsyu.academicgraph;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文加载冒烟测试：验证 Spring 容器能完整装配（Web/Security/JPA/Neo4j 各层 Bean）。
 * 连本机真实 MySQL/Neo4j（local profile 密码在 gitignored 的 application-local.yaml 里）。
 * 接口级契约测试见 ApiIntegrationTests。
 */
@SpringBootTest
@ActiveProfiles("local")
class AcademicGraphApplicationTests {

    @Test
    void contextLoads() {
    }

}
