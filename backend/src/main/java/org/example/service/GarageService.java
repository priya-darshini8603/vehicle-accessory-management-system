package org.example.service;

import org.example.dto.CompatibilityResponse;
import org.example.dto.GarageVehicleRequest;
import org.example.dto.GarageVehicleResponse;
import org.example.dto.GarageVehicleUpdateRequest;

import java.util.List;

// A signed-in user's own vehicles ("my garage"). Every method is scoped to the caller: another
// user's vehicle id behaves exactly like a missing one (404).
public interface GarageService {

    List<GarageVehicleResponse> getGarage(String ownerEmail);

    GarageVehicleResponse getVehicle(String ownerEmail, Long vehicleId);

    GarageVehicleResponse addVehicle(String ownerEmail, GarageVehicleRequest request);

    GarageVehicleResponse updateVehicle(String ownerEmail, Long vehicleId, GarageVehicleUpdateRequest request);

    GarageVehicleResponse makePrimary(String ownerEmail, Long vehicleId);

    // Unlinks the vehicle from the caller (e.g. it was sold). The vehicle and its accessory
    // mappings stay in the catalog, so the next owner can add it to their own garage.
    void removeVehicle(String ownerEmail, Long vehicleId);

    CompatibilityResponse getCompatibleAccessories(String ownerEmail, Long vehicleId);
}
