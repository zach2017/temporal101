package com.example.common;

/**
 * A shared data model that both Service-A and Service-B use.
 * Java 21 record — immutable, compact, with auto-generated
 * equals/hashCode/toString.
 */
public record User(String id, String name, String email) {

    /**
     * Factory method with validation (shared business rule).
     */
    public static User create(String id, String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        return new User(id, name, email.toLowerCase());
    }
}
