package org.example.dto;

import java.time.Instant;
import java.util.List;

public record InventoryReport(
        Instant generatedAt,
        int analysisPeriodDays,
        List<InventoryItemReport> products,
        List<InventoryItemReport> lowStockItems,
        List<InventoryItemReport> outOfStockItems,
        List<InventoryItemReport> fastMovingProducts,
        List<InventoryItemReport> slowMovingProducts,
        List<StockMovementResponse> recentMovements
) {}
