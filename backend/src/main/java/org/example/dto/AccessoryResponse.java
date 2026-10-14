package org.example.dto;

import java.math.BigDecimal;
import java.time.Instant;

// Data returned by the accessory API.
public record AccessoryResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        String description,
        Integer stockQuantity,
        Instant createdAt,
        Instant updatedAt
) {}
