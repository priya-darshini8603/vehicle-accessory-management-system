package org.example.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceChangeResponse(
        Long id,
        Long accessoryId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        BigDecimal oldCostPrice,
        BigDecimal newCostPrice,
        String changedBy,
        Instant changedAt
) {}
