package org.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        // Admin responses only. Customer-facing responses leave it null, so it is omitted from the JSON.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal costPrice,
        // GST percent; null means the configured default rate applies
        BigDecimal taxRate,
        Instant createdAt,
        Instant updatedAt
) {}
