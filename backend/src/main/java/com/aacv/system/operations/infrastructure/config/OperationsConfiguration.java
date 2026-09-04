package com.aacv.system.operations.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OperationsProperties.class)
class OperationsConfiguration {
}
