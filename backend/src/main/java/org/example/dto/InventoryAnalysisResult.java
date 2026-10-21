package org.example.dto;

import java.math.BigDecimal;

public record InventoryAnalysisResult(
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
