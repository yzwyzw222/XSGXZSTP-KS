package com.aacv.system.export.infrastructure.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aacv.export")
public class ExportProperties {
    private String rootDirectory = Path.of(System.getProperty("java.io.tmpdir"), "aacv-system", "exports").toString();
    private int maxRecords = 10_000;
    private int userActiveLimit = 2;
    private int concurrency = 2;
    private int queueCapacity = 20;
    private int retentionHours = 24;

    public String getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public int getUserActiveLimit() {
        return userActiveLimit;
    }

    public void setUserActiveLimit(int userActiveLimit) {
        this.userActiveLimit = userActiveLimit;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getRetentionHours() {
        return retentionHours;
    }

    public void setRetentionHours(int retentionHours) {
        this.retentionHours = retentionHours;
    }

    public void validate() {
        if (rootDirectory == null || rootDirectory.isBlank()
                || maxRecords < 1 || maxRecords > 10_000
                || userActiveLimit < 1 || userActiveLimit > 20
                || concurrency < 1 || concurrency > 8
                || queueCapacity < 1 || queueCapacity > 200
                || retentionHours < 1 || retentionHours > 168) {
            throw new IllegalStateException("导出配置无效");
        }
    }
}
