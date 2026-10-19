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
        int recommendedReorderQuantity
) {}
