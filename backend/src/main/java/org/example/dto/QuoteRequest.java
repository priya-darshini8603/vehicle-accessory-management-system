package org.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

// Payload for POST /api/pricing/quote. vehicleId (optional) checks every item fits that vehicle;
// customers may only quote against vehicles in their own garage.
public record QuoteRequest(

        @NotEmpty(message = "At least one item is required")
        @Size(max = 50, message = "A quote can contain at most 50 items")
        List<@Valid @NotNull(message = "Item must not be null") Item> items,

        @Size(max = 32, message = "Coupon code must not exceed 32 characters")
        String couponCode,

        @Positive(message = "Vehicle id must be positive")
        Long vehicleId
) {

    public static final int MAX_QUANTITY_PER_ACCESSORY = 100;

    public record Item(
            @NotNull(message = "Accessory id is required")
            @Positive(message = "Accessory id must be positive")
            Long accessoryId,

            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = MAX_QUANTITY_PER_ACCESSORY, message = "Quantity must not exceed 100")
            Integer quantity
    ) {}
}
