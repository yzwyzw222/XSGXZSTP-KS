package com.aacv.system.graph.infrastructure.neo4j;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;

@Configuration(proxyBeanMethods = false)
class Neo4jTransactionConfiguration {

    @Bean("neo4jTransactionManager")
    Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}
