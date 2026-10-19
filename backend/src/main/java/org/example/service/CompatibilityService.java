package org.example.service;

import org.example.dto.CompatibilityResponse;

import java.util.List;

/**
 * Looks up which accessories are compatible with (mapped to) vehicles.
 */
public interface CompatibilityService {

    CompatibilityResponse findByVehicleNumber(String vehicleNumber);

    List<CompatibilityResponse> findByModelName(String modelName);
}
