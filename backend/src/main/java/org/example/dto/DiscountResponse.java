package org.example.dto;

import org.example.enums.DiscountScope;
import org.example.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record DiscountResponse(
        Long id,
        String name,
        String code,
        DiscountType type,
        BigDecimal value,
        DiscountScope scope,
        String category,
        Long accessoryId,
        BigDecimal maxDiscountAmount,
        int minQuantity,
        Instant validFrom,
        Instant validTo,
        boolean active,
        // Active and inside its validity window right now
        boolean live,
        Instant createdAt,
        Instant updatedAt,
        // Send back on PUT; see DiscountRequest.version
        Long version
) {}
