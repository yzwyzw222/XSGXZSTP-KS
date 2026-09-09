package com.example.academic_entity_extract_kg_construction.infrastructure.exception;

public class AuthorNotFoundException extends RuntimeException {
    public AuthorNotFoundException(String message) {
        super(message);
    }

    public AuthorNotFoundException(Long id) {
        super("No author exists with id " + id);
    }
}
