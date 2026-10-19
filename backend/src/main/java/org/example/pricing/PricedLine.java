package org.example.pricing;

import org.example.entity.Discount;

import java.math.BigDecimal;

// Result of pricing one accessory at a quantity. All amounts are rounded to 2 decimals.
public record PricedLine(
        BigDecimal unitPrice,
        BigDecimal unitDiscount,
        BigDecimal discountedUnitPrice,
        int quantity,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        // The single discount that won for this line, or null
        Discount appliedDiscount,
        // True when the winning discount was cut back so the price stays at or above cost
        boolean marginFloorApplied
) {}
