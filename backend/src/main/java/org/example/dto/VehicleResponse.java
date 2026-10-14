package org.example.dto;

import java.time.Instant;
import java.util.List;

// Vehicle details returned by the admin API, including its mapped accessories.
public record VehicleResponse(
        Long id,
        String vehicleNumber,
        String make,
        String model,
        Integer year,
        Long userId,
        List<AccessoryResponse> accessories,
        Instant createdAt,
        Instant updatedAt
) {}
