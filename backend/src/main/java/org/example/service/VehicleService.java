package org.example.service;

import org.example.dto.VehicleDto;
import java.util.List;

public interface VehicleService {
    VehicleDto createVehicle(VehicleDto vehicleDto);
    List<VehicleDto> getAllVehicles();
    VehicleDto getVehicleById(Long id);
    VehicleDto updateVehicle(Long id, VehicleDto vehicleDto);
    void deleteVehicle(Long id);

    // New Many-to-Many method
    VehicleDto assignAccessoryToVehicle(Long vehicleId, Long accessoryId);
}