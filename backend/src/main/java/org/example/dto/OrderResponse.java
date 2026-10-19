package org.example.dto;

import org.example.enums.InventoryProcessingStatus;
import org.example.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        InventoryProcessingStatus inventoryProcessingStatus,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal finalTotal,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items
) {
    public record Item(
            Long accessoryId,
            String accessoryName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal unitDiscount,
            String discountName,
            BigDecimal taxRate,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {}
}
