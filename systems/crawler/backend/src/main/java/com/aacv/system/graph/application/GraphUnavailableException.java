package com.aacv.system.graph.application;

public class GraphUnavailableException extends RuntimeException {

    public GraphUnavailableException() {
        super("图投影当前不可用");
    }
}
