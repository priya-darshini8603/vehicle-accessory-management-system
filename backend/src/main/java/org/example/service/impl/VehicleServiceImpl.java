package org.example.service.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.example.dto.VehicleDto;
import org.example.entity.Accessory;
import org.example.entity.User;
import org.example.entity.Vehicle;
import org.example.mapper.VehicleMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.UserRepository;
import org.example.repository.VehicleRepository;
import org.example.service.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final AccessoryRepository accessoryRepository;
    private final UserRepository userRepository;
    @Override
    public VehicleDto createVehicle(VehicleDto vehicleDto) {
        if (vehicleDto.getVehicleNumber() != null && vehicleRepository.existsByVehicleNumber(vehicleDto.getVehicleNumber())) {
            throw new IllegalArgumentException("Vehicle with number '" + vehicleDto.getVehicleNumber() + "' already exists!");
        }
        Vehicle vehicle = VehicleMapper.mapToVehicle(vehicleDto, new Vehicle());

        if (vehicleDto.getUserId() != null) {
            User user = userRepository.findById(vehicleDto.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + vehicleDto.getUserId()));
            vehicle.setUser(user);
        }

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        return VehicleMapper.mapToVehicleDto(savedVehicle);
    }

    @Override
    public List<VehicleDto> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(VehicleMapper::mapToVehicleDto)
                .collect(Collectors.toList());
    }

    @Override
    public VehicleDto getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));
        return VehicleMapper.mapToVehicleDto(vehicle);
    }

    @Override
    public VehicleDto updateVehicle(Long id, VehicleDto vehicleDto) {
        Vehicle existingVehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));

        if (vehicleDto.getVehicleNumber() != null &&
                !existingVehicle.getVehicleNumber().equals(vehicleDto.getVehicleNumber()) &&
                vehicleRepository.existsByVehicleNumber(vehicleDto.getVehicleNumber())) {
            throw new IllegalArgumentException("Vehicle number already exists: " + vehicleDto.getVehicleNumber());
        }

        existingVehicle.setVehicleNumber(vehicleDto.getVehicleNumber());
        existingVehicle.setMake(vehicleDto.getMake());
        existingVehicle.setModel(vehicleDto.getModel());
        existingVehicle.setYear(vehicleDto.getYear());

        if (vehicleDto.getUserId() != null) {
            User user = userRepository.findById(vehicleDto.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + vehicleDto.getUserId()));
            existingVehicle.setUser(user);
        } else {
            existingVehicle.setUser(null);
        }

        Vehicle updatedVehicle = vehicleRepository.save(existingVehicle);
        return VehicleMapper.mapToVehicleDto(updatedVehicle);
    }

    @Override
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));
        vehicleRepository.delete(vehicle);
    }

    @Override
    @Transactional
    public VehicleDto assignAccessoryToVehicle(Long vehicleId, Long accessoryId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));

        Accessory accessory = accessoryRepository.findById(accessoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Accessory not found"));

        // Add accessory to vehicle's accessory set (Many-to-Many link)
        vehicle.getAccessories().add(accessory);

        // Optional: If bidirectional, also add vehicle to accessory's vehicle set
        accessory.getVehicles().add(vehicle);

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        return VehicleMapper.mapToVehicleDto(savedVehicle);
    }
}