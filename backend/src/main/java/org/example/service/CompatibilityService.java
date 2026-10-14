package org.example.service;

import org.example.dto.CompatibilityResponse;

/**
 * Looks up which accessories are compatible with (mapped to) a vehicle.
 */
public interface CompatibilityService {

    CompatibilityResponse findByVehicleNumber(String vehicleNumber);
}
