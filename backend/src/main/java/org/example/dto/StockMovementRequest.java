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
    @AssertTrue(message = "SALE quantities must be negative, RESTORATION quantities must be positive, "
            + "and ADJUSTMENT quantities must be non-zero")
    public boolean isValidMovement() {
        if (type == null || quantityDelta == null) {
            return true;
        }
        return switch (type) {
            case SALE -> quantityDelta < 0;
            case RESTORATION -> quantityDelta > 0;
            case ADJUSTMENT -> quantityDelta != 0;
        };
    }
}
