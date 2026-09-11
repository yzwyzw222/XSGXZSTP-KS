package com.aacv.system.authorimport.infrastructure;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImportUploadConfiguration {
    @Bean
    MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", ScholarTableReader.MAX_BYTES, ScholarTableReader.MAX_BYTES + 65536L, 0);
    }
}
