package org.example.dto;

import java.time.Instant;

// One vehicle in a customer's garage.
public record GarageVehicleResponse(
        Long id,
        String vehicleNumber,
        String make,
        String model,
        Integer year,
        String nickname,
        boolean primary,
        long compatibleAccessoryCount,
        Instant createdAt
) {}
