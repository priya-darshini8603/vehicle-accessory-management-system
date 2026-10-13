package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request model for creating or updating an accessory item in the store inventory.
 */
public record AccessoryRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotNull @PositiveOrZero Double price,
        @NotNull @PositiveOrZero Integer stockQuantity
) {}
