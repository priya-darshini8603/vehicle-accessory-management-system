package org.example.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryItemReport(
        Long accessoryId,
        String name,
        String category,
        Instant updatedAt,
        int currentStock,
        int minimumStock,
        int leadTimeDays,
        long unitsSold,
        BigDecimal averageDailySales,
        BigDecimal leadTimeDemand,
        BigDecimal reorderPoint,
        BigDecimal stockCoverDays,
        boolean lowStock,
        boolean outOfStock,
        int recommendedReorderQuantity
) {}
