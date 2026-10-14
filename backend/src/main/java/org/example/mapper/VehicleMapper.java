package org.example.mapper;

import org.example.dto.VehicleDto;
import org.example.entity.Accessory;
import org.example.entity.Vehicle;

import java.util.stream.Collectors;

public class VehicleMapper {

    // Convert Vehicle Entity to VehicleDto
    public static VehicleDto mapToVehicleDto(Vehicle vehicle) {
        VehicleDto dto = new VehicleDto();
        dto.setId(vehicle.getId());
        dto.setVehicleNumber(vehicle.getVehicleNumber());
        dto.setMake(vehicle.getMake());
        dto.setModel(vehicle.getModel());
        dto.setYear(vehicle.getYear());

        // Handle potential null user safely
        if (vehicle.getUser() != null) {
            dto.setUserId(vehicle.getUser().getId());
        }
        if (vehicle.getAccessories() != null) {
            dto.setAccessoryIds(
                    vehicle.getAccessories()
                            .stream()
                            .map(Accessory::getId)
                            .collect(Collectors.toSet())
            );
        }

        return dto;
    }

    // Convert VehicleDto to Vehicle Entity
    public static Vehicle mapToVehicle(VehicleDto dto, Vehicle vehicle) {
        vehicle.setVehicleNumber(dto.getVehicleNumber());
        vehicle.setMake(dto.getMake());
        vehicle.setModel(dto.getModel());
        vehicle.setYear(dto.getYear());
        // Note: User assignment is typically handled in the service layer using UserRepository
        return vehicle;
    }
}