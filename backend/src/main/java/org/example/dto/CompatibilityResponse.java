package org.example.dto;

import java.util.List;

// Result of a compatibility search: the vehicle's metadata plus every accessory mapped to it.
public record CompatibilityResponse(
        VehicleSummary vehicle,
        int totalAccessories,
        List<AccessoryResponse> compatibleAccessories
) {

    // Lightweight vehicle metadata (no audit or ownership fields) for customer-facing responses.
    public record VehicleSummary(
            Long id,
            String vehicleNumber,
            String make,
            String model,
            Integer year
    ) {}
}
