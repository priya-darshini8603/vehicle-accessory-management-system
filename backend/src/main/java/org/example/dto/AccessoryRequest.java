package org.example.dto;

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
        Integer stockQuantity
) {
    public AccessoryRequest {
        name = trim(name);
        category = trim(category);
        description = trim(description);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
