package org.example.exception;

// Thrown when a request is well-formed but not allowed by a business rule; mapped to 422.
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
