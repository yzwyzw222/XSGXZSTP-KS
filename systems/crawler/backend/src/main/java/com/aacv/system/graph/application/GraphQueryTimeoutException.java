package com.aacv.system.graph.application;

public class GraphQueryTimeoutException extends RuntimeException {

    public GraphQueryTimeoutException() {
        super("图查询超时，请缩小查询范围后重试");
    }
}
