package org.example.mapper;

import lombok.RequiredArgsConstructor;
import org.example.dto.AccessoryResponse;
import org.example.dto.CompatibilityResponse;
import org.example.dto.VehicleRequest;
import org.example.dto.VehicleResponse;
import org.example.entity.Accessory;
import org.example.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VehicleMapper {

    private final AccessoryMapper accessoryMapper;

    // Builds a new (transient) entity from a validated request. Owner assignment is done in the service.
    public Vehicle toEntity(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        updateEntity(vehicle, request);
        return vehicle;
    }

    // Copies editable fields onto an existing entity (used for PUT).
    public void updateEntity(Vehicle vehicle, VehicleRequest request) {
        vehicle.setVehicleNumber(request.vehicleNumber());
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setYear(request.year());
    }

    // Requires the accessories collection to be initialized (load via a fetch-join repository method).
    public VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear(),
                // getId() on a lazy proxy does not trigger initialization
                vehicle.getUser() != null ? vehicle.getUser().getId() : null,
                sortedAccessories(vehicle),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }

    public CompatibilityResponse toCompatibilityResponse(Vehicle vehicle) {
        List<AccessoryResponse> accessories = sortedAccessories(vehicle);
        return new CompatibilityResponse(
                new CompatibilityResponse.VehicleSummary(
                        vehicle.getId(),
                        vehicle.getVehicleNumber(),
                        vehicle.getMake(),
                        vehicle.getModel(),
                        vehicle.getYear()),
                accessories.size(),
                accessories);
    }

    // Deterministic ordering (category, then name) since the underlying collection is a Set.
    private List<AccessoryResponse> sortedAccessories(Vehicle vehicle) {
        return vehicle.getAccessories().stream()
                .sorted(Comparator.comparing(Accessory::getCategory, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Accessory::getName, String.CASE_INSENSITIVE_ORDER))
                .map(accessoryMapper::toResponse)
                .toList();
    }
}
