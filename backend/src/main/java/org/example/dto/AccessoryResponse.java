package org.example.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public response shape exposed through the accessories API.
 */
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
