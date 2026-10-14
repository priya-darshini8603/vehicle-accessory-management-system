package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Holds the data for adding or updating an accessory.
public record AccessoryRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotNull @PositiveOrZero Double price,
        @NotNull @PositiveOrZero Integer stockQuantity
) {}
