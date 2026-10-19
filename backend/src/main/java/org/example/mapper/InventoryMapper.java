package org.example.mapper;

import org.example.dto.InventoryItemReport;
import org.example.dto.StockMovementResponse;
import org.example.entity.Accessory;
import org.example.entity.StockMovement;
import org.example.enums.StockMovementType;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

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

        double averageDailySales =
                (double) unitsSold / analysisPeriodDays;

        double leadTimeDemand =
                averageDailySales * leadTimeDays;

        double reorderPoint =
                leadTimeDemand + minimumStock;

        Double stockCoverDays =
                averageDailySales > 0
                        ? stock / averageDailySales
                        : null;

        int recommendedReorderQuantity =
                (int) Math.min(
                        Integer.MAX_VALUE,
                        Math.max(
                                0,
                                Math.ceil(reorderPoint - stock)));

        return new InventoryItemReport(
                accessory.getId(),
                accessory.getName(),
                accessory.getCategory(),
                stock,
                minimumStock,
                leadTimeDays,
                unitsSold,
                averageDailySales,
                leadTimeDemand,
                reorderPoint,
                stockCoverDays,
                stock > 0 && stock <= minimumStock,
                stock == 0,
                recommendedReorderQuantity
        );
    }

    private int defaultValue(Integer value) {
        return value == null ? 0 : value;
    }
}