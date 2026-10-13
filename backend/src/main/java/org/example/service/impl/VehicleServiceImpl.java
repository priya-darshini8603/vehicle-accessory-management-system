package org.example.service.impl;

import lombok.AllArgsConstructor;
import org.example.entity.Vehicle;
import org.example.repository.VehicleRepository;
import org.example.service.VehicleService;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Override
    public Vehicle createVehicle(Vehicle vehicle) {
        // Validate if vehicle number already exists to prevent duplicate entry errors
        if (vehicle.getVehicleNumber() != null && vehicleRepository.existsByVehicleNumber(vehicle.getVehicleNumber())) {
            throw new IllegalArgumentException("Vehicle with number '" + vehicle.getVehicleNumber() + "' already exists!");
        }
        return vehicleRepository.save(vehicle);
    }
}