package com.example.academic_entity_extract_kg_construction.infrastructure.exception;

public class PaperNotFoundException extends RuntimeException {
    public PaperNotFoundException(String message) {
        super(message);
    }

    public PaperNotFoundException(Long id) {
        super("No paper exists with id " + id);
    }
}
