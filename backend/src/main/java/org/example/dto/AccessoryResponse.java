package org.example.dto;

// Data returned by the accessory API.
public record AccessoryResponse(
        Long id,
        String name,
        String category,
        Double price,
        Integer stockQuantity
) {}
