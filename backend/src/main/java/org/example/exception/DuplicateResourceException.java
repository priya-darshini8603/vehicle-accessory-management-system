package org.example.exception;

// Thrown when creating/updating would violate a uniqueness rule (vehicle number, email); mapped to 409.
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
