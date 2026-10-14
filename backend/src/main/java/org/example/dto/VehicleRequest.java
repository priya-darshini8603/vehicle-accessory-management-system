package org.example.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.entity.Vehicle;

// Payload for creating or updating a vehicle (admin only).
// The compact constructor normalizes input before Bean Validation runs, so " ka01ab1234 "
// is validated and stored as "KA01AB1234".
public record VehicleRequest(

        @NotBlank(message = "Vehicle number is required")
        @Size(min = 4, max = 32, message = "Vehicle number must contain 4 to 32 characters")
        @Pattern(regexp = "^[A-Z0-9-]*$",
                message = "Vehicle number may only contain letters, digits and hyphens")
        String vehicleNumber,

        @NotBlank(message = "Make is required")
        @Size(max = 60, message = "Make must not exceed 60 characters")
        String make,

        @NotBlank(message = "Model is required")
        @Size(max = 60, message = "Model must not exceed 60 characters")
        String model,

        @NotNull(message = "Year is required")
        @Min(value = 1886, message = "Year must be 1886 or later")
        @Max(value = 2100, message = "Year must be 2100 or earlier")
        Integer year,

        // Optional owner (customer) of the vehicle
        @Positive(message = "User id must be positive")
        Long userId
) {
    public VehicleRequest {
        vehicleNumber = Vehicle.normalizeVehicleNumber(vehicleNumber);
        make = trim(make);
        model = trim(model);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
