package org.example.dto;

public record InventoryItemReport(
        Long accessoryId,
        String name,
        String category,
        int currentStock,
        int minimumStock,
        int leadTimeDays,
        long unitsSold,
        double averageDailySales,
        double leadTimeDemand,
        double reorderPoint,
        Double stockCoverDays,
        boolean lowStock,
        boolean outOfStock,
        int recommendedReorderQuantity
) {}
