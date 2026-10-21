package org.example.service;

import org.example.dto.InventoryAnalysisResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class InventoryAnalysisCalculator {

    private static final int ANALYTICS_SCALE = 6;

    public InventoryAnalysisResult calculate(
            int stock,
            Integer minimumStock,
            Integer leadTimeDays,
            long unitsSold,
            int analysisPeriodDays) {

        if (analysisPeriodDays <= 0) {
            throw new IllegalArgumentException("Analysis period must be positive");
        }

        int normalizedMinimumStock = defaultValue(minimumStock);
        int normalizedLeadTimeDays = Math.max(0, defaultValue(leadTimeDays));

        // Average daily sales = max(units sold, 0) / analysis period days.
        BigDecimal averageDailySales =
                decimal(unitsSold)
                        .divide(BigDecimal.valueOf(analysisPeriodDays), ANALYTICS_SCALE, RoundingMode.HALF_UP);

        // Lead-time demand = average daily sales * lead-time days.
        BigDecimal leadTimeDemand =
                averageDailySales.multiply(BigDecimal.valueOf(normalizedLeadTimeDays));

        // Reorder point = lead-time demand + minimum stock (safety stock).
        BigDecimal reorderPoint =
                leadTimeDemand.add(BigDecimal.valueOf(normalizedMinimumStock));

        // Stock cover days = current stock / average daily sales; undefined when sales are zero.
        BigDecimal stockCoverDays =
                averageDailySales.signum() > 0
                        ? BigDecimal.valueOf(stock).divide(averageDailySales, ANALYTICS_SCALE, RoundingMode.HALF_UP)
                        : null;

        // Low stock = stock is positive and at or below the reorder point.
        // Out of stock = current stock is zero.
        return new InventoryAnalysisResult(
                stock,
                normalizedMinimumStock,
                normalizedLeadTimeDays,
                unitsSold,
                averageDailySales,
                leadTimeDemand,
                reorderPoint,
                stockCoverDays,
                stock > 0 && BigDecimal.valueOf(stock).compareTo(reorderPoint) <= 0,
                stock == 0,
                recommendedReorderQuantity(reorderPoint, stock)
        );
    }

    private static int recommendedReorderQuantity(BigDecimal reorderPoint, int stock) {
        // Recommended reorder quantity = max(0, ceil(reorder point - current stock)).
        BigDecimal quantity = reorderPoint.subtract(BigDecimal.valueOf(stock));
        if (quantity.signum() <= 0) {
            return 0;
        }
        BigDecimal roundedQuantity = quantity.setScale(0, RoundingMode.CEILING);
        return roundedQuantity.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0
                ? Integer.MAX_VALUE
                : roundedQuantity.intValueExact();
    }

    private static BigDecimal decimal(long value) {
        return BigDecimal.valueOf(Math.max(0, value));
    }

    private static int defaultValue(Integer value) {
        return value == null ? 0 : value;
    }
}
