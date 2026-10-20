package org.example.mapper;

import org.example.dto.InventoryItemReport;
import org.example.dto.StockMovementResponse;
import org.example.entity.Accessory;
import org.example.entity.StockMovement;
import org.example.enums.StockMovementType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class InventoryMapper {

    private static final int ANALYTICS_SCALE = 6;

    public StockMovement toMovement(
            Accessory accessory,
            StockMovementType type,
            int quantityDelta,
            int stockBefore,
            int stockAfter,
            String note,
            String actor) {

        StockMovement movement = new StockMovement();
        movement.setAccessory(accessory);
        movement.setType(type);
        movement.setQuantityDelta(quantityDelta);
        movement.setStockBefore(stockBefore);
        movement.setStockAfter(stockAfter);
        movement.setNote(note);
        movement.setChangedBy(actor);

        return movement;
    }

    public StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getAccessory().getId(),
                movement.getAccessory().getName(),
                movement.getType(),
                movement.getQuantityDelta(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getNote(),
                movement.getChangedBy(),
                movement.getOccurredAt()
        );
    }

    public InventoryItemReport toInventoryItemReport(
            Accessory accessory,
            int stock,
            long unitsSold,
            int analysisPeriodDays) {

        int minimumStock = defaultValue(accessory.getMinStockThreshold());

        int leadTimeDays = Math.max(
                0,
                defaultValue(accessory.getLeadTimeDays()));

        BigDecimal averageDailySales =
                decimal(unitsSold)
                        .divide(BigDecimal.valueOf(analysisPeriodDays), ANALYTICS_SCALE, RoundingMode.HALF_UP);

        BigDecimal leadTimeDemand =
                averageDailySales.multiply(BigDecimal.valueOf(leadTimeDays));

        BigDecimal reorderPoint =
                leadTimeDemand.add(BigDecimal.valueOf(minimumStock));

        BigDecimal stockCoverDays =
                averageDailySales.signum() > 0
                        ? BigDecimal.valueOf(stock).divide(averageDailySales, ANALYTICS_SCALE, RoundingMode.HALF_UP)
                        : null;

        int recommendedReorderQuantity = recommendedReorderQuantity(reorderPoint, stock);

        return new InventoryItemReport(
                accessory.getId(),
                accessory.getName(),
                accessory.getCategory(),
                accessory.getUpdatedAt(),
                stock,
                minimumStock,
                leadTimeDays,
                unitsSold,
                averageDailySales,
                leadTimeDemand,
                reorderPoint,
                stockCoverDays,
                stock > 0 && BigDecimal.valueOf(stock).compareTo(reorderPoint) <= 0,
                stock == 0,
                recommendedReorderQuantity
        );
    }

    private static int recommendedReorderQuantity(BigDecimal reorderPoint, int stock) {
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