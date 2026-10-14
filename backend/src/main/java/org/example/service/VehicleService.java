package org.example.service;

import org.example.dto.PageResponse;
import org.example.dto.VehicleRequest;
import org.example.dto.VehicleResponse;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface VehicleService {

    VehicleResponse createVehicle(VehicleRequest request);

    PageResponse<VehicleResponse> getVehicles(Pageable pageable);

    VehicleResponse getVehicleById(Long id);

    VehicleResponse updateVehicle(Long id, VehicleRequest request);

    void deleteVehicle(Long id);

    // Vehicle–accessory mappings

    VehicleResponse addAccessory(Long vehicleId, Long accessoryId);

    VehicleResponse removeAccessory(Long vehicleId, Long accessoryId);

    VehicleResponse replaceAccessories(Long vehicleId, Set<Long> accessoryIds);
}
