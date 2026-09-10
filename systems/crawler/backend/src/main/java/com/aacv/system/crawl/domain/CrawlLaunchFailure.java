package com.aacv.system.crawl.domain;

/** 启动失败只公开固定分类和处理建议，避免异常中包含连接信息或来源凭据。 */
public enum CrawlLaunchFailure {
    EXECUTOR_BUSY("采集执行队列已满或服务正在关闭，请稍后对该任务重新执行。"),
    STORAGE_UNAVAILABLE("批次启动时无法访问任务数据库，请检查数据库连接和批处理元数据表后重新执行。"),
    TRANSACTION_FAILED("批次启动事务失败，请管理员检查事务配置后重新执行。"),
    INVALID_PARAMETERS("批次启动参数无效，请检查任务范围后重新执行。"),
    UNKNOWN("批次未能启动，请在统一门户的日志管理中查看启动失败事件及对应运行编号，排查后重新执行任务。");

    private final String message;

    CrawlLaunchFailure(String message) { this.message = message; }

    public String message() { return message; }
}
