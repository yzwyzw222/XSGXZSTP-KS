package com.example.academic_entity_extract_kg_construction.infrastructure.exception;

public class GraphUnavailableException extends RuntimeException {
    public GraphUnavailableException(String message) {
        super(message);
    }

    public GraphUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
