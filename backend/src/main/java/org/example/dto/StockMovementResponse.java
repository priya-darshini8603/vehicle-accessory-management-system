package org.example.dto;

import org.example.enums.StockMovementType;

import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long accessoryId,
        String accessoryName,
        StockMovementType type,
        int quantityDelta,
        int stockBefore,
        int stockAfter,
        String note,
        String changedBy,
        Instant occurredAt
) {}
