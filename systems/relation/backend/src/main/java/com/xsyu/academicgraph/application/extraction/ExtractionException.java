package com.xsyu.academicgraph.application.extraction;

/**
 * LLM 抽取链路异常（从 feature/Du 移植）。
 * 覆盖三类失败：调用 LLM 失败（网络/鉴权/超时）、LLM 返回的不是合法 JSON、响应结构缺失。
 * 异步线程里被 ExtractionService 捕获后把论文状态标成 FAILED，不会把异常抛到前端。
 */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
