package org.example.dto;

import org.example.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Itemised price breakdown. Totals are the exact sum of the line amounts.
public record QuoteResponse(
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String currency,
        String couponCode,
        boolean couponApplied,
        Long vehicleId,
        // Quote-level notices (e.g. a better automatic discount beat the coupon)
        List<String> warnings,
        // Prices can change later; a quote reflects the catalog at this instant
        Instant quotedAt
) {

    public record Line(
            Long accessoryId,
            String name,
            String category,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal unitDiscount,
            BigDecimal discountedUnitPrice,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal total,
            AppliedDiscount appliedDiscount,
            // Line-level notices: stock shortfall, not compatible with the chosen vehicle, margin floor
            List<String> warnings
    ) {}

    public record AppliedDiscount(
            Long id,
            String name,
            String code,
            DiscountType type,
            BigDecimal value
    ) {}
}
