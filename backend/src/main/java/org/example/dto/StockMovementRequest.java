package org.example.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.enums.StockMovementType;

public record StockMovementRequest(
        @NotNull StockMovementType type,
        @NotNull Integer quantityDelta,
        @Size(max = 500) String note
) {
    @AssertTrue(message = "Sales must decrease stock; purchases and returns must increase stock; adjustments must be non-zero")
    public boolean isValidMovement() {
        if (type == null || quantityDelta == null) {
            return true;
        }
        return switch (type) {
            case SALE -> quantityDelta < 0;
            case PURCHASE, RETURN -> quantityDelta > 0;
            case ADJUSTMENT -> quantityDelta != 0;
        };
    }
}
