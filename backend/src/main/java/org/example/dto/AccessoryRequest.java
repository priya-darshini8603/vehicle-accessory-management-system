package org.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Holds the data for adding or updating an accessory.
public record AccessoryRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must not exceed 50 characters")
        String category,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must not be negative")
        @Digits(integer = 10, fraction = 2, message = "Price must have at most 10 digits and 2 decimals")
        BigDecimal price,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        // Optional inventory count
        @PositiveOrZero(message = "Stock quantity must not be negative")
        Integer stockQuantity,

        // Optional purchase cost; discounts never take the selling price below it
        @DecimalMin(value = "0.00", message = "Cost price must not be negative")
        @Digits(integer = 10, fraction = 2, message = "Cost price must have at most 10 digits and 2 decimals")
        BigDecimal costPrice,

        // Optional GST percent; the configured default applies when omitted
        @DecimalMin(value = "0.00", message = "Tax rate must not be negative")
        @DecimalMax(value = "100.00", message = "Tax rate must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Tax rate must have at most 2 decimals")
        BigDecimal taxRate,

        @PositiveOrZero(message = "Minimum stock threshold must not be negative")
        Integer minStockThreshold,

        @PositiveOrZero(message = "Lead time must not be negative")
        Integer leadTimeDays,

        @Size(max = 500, message = "Stock adjustment reason must not exceed 500 characters")
        String stockAdjustmentReason
) {
    // Keeps existing callers (and clients) that don't send cost or tax working.
    public AccessoryRequest(String name, String category, BigDecimal price, String description, Integer stockQuantity) {
        this(name, category, price, description, stockQuantity, null, null, null, null, null);
    }

    public AccessoryRequest(String name, String category, BigDecimal price, String description, Integer stockQuantity,
                            BigDecimal costPrice, BigDecimal taxRate) {
        this(name, category, price, description, stockQuantity, costPrice, taxRate, null, null, null);
    }

    public AccessoryRequest {
        name = trim(name);
        category = trim(category);
        description = trim(description);
        stockAdjustmentReason = trim(stockAdjustmentReason);
    }

    // Selling below cost is almost always a typo (₹899 instead of ₹8,999); reject it at entry.
    @JsonIgnore
    @AssertTrue(message = "Price must not be lower than the cost price")
    public boolean isPriceNotBelowCost() {
        return price == null || costPrice == null || price.compareTo(costPrice) >= 0;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
