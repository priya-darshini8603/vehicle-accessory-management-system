package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.VehicleDto;
import org.example.service.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    // ADMIN only: Create vehicle
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleDto> createVehicle(@Valid @RequestBody VehicleDto vehicleDto) {
        VehicleDto savedVehicle = vehicleService.createVehicle(vehicleDto);
        return new ResponseEntity<>(savedVehicle, HttpStatus.CREATED);
    }

    // View all vehicles
    @GetMapping
    public ResponseEntity<List<VehicleDto>> getAllVehicles() {
        return ResponseEntity.ok(vehicleService.getAllVehicles());
    }

    // View vehicle by ID
    @GetMapping("/{id}")
    public ResponseEntity<VehicleDto> getVehicleById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }

    // ADMIN only: Update vehicle
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleDto> updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleDto vehicleDto) {
        VehicleDto updatedVehicle = vehicleService.updateVehicle(id, vehicleDto);
        return ResponseEntity.ok(updatedVehicle);
    }

    // ADMIN only: Delete vehicle
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.ok("Vehicle deleted successfully.");
    }

    // ADMIN only: Assign accessory to vehicle
    @PostMapping("/{vehicleId}/accessories/{accessoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleDto> assignAccessoryToVehicle(
            @PathVariable Long vehicleId,
            @PathVariable Long accessoryId) {
        VehicleDto updatedVehicle = vehicleService.assignAccessoryToVehicle(vehicleId, accessoryId);
        return ResponseEntity.ok(updatedVehicle);
    }
}