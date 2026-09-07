package com.aacv.system.graph.domain;

public record GraphTypeDefinition(
        Kind kind, String code, String displayName, String color, int size,
        ReviewStatus reviewStatus, long version) {
    public enum Kind { NODE, RELATIONSHIP }
    public enum ReviewStatus { PENDING, APPROVED, REJECTED }
}
