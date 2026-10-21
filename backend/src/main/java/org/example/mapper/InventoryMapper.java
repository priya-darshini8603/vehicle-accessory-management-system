package org.example.mapper;

import org.example.dto.InventoryAnalysisResult;
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
            InventoryAnalysisResult analysis) {

        return new InventoryItemReport(
                accessory.getId(),
                accessory.getName(),
                accessory.getCategory(),
                accessory.getUpdatedAt(),
                analysis.currentStock(),
                analysis.minimumStock(),
                analysis.leadTimeDays(),
                analysis.unitsSold(),
                analysis.averageDailySales(),
                analysis.leadTimeDemand(),
                analysis.reorderPoint(),
                analysis.stockCoverDays(),
                analysis.lowStock(),
                analysis.outOfStock(),
                analysis.recommendedReorderQuantity()
        );
    }
}