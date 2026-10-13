package org.example.dto;

/**
 * Public response shape exposed through the accessories API.
 */
public record AccessoryResponse(
        Long id,
        String name,
        String category,
        Double price,
        Integer stockQuantity
) {}
