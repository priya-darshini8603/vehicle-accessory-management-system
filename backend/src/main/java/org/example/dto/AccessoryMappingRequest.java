package org.example.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

// Bulk mapping payload for PUT /api/admin/vehicles/{vehicleId}/accessories.
// Replaces the vehicle's accessories with exactly this set; an empty set clears all mappings.
public record AccessoryMappingRequest(

        @NotNull(message = "accessoryIds is required (use an empty list to clear mappings)")
        @Size(max = 500, message = "At most 500 accessories can be mapped in one request")
        Set<@NotNull(message = "Accessory id must not be null")
            @Positive(message = "Accessory id must be positive") Long> accessoryIds
) {}
