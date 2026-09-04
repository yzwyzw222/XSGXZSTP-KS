package com.aacv.system.graph.application;

public class GraphRebuildInProgressException extends RuntimeException {

    public GraphRebuildInProgressException() {
        super("图投影正在全量重建");
    }
}
