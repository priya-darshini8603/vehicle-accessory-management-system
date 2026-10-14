package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AccessoryMappingRequest;
import org.example.dto.PageResponse;
import org.example.dto.VehicleRequest;
import org.example.dto.VehicleResponse;
import org.example.service.VehicleService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/vehicles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse created = vehicleService.createVehicle(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Supports ?page=0&size=20&sort=vehicleNumber,asc
    @GetMapping
    public ResponseEntity<PageResponse<VehicleResponse>> getVehicles(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(vehicleService.getVehicles(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicleById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, request));
    }

    // Also removes the vehicle's accessory mappings; the accessories themselves are kept.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }

    // --- Vehicle–accessory mappings ------------------------------------------------------

    @PostMapping("/{vehicleId}/accessories/{accessoryId}")
    public ResponseEntity<VehicleResponse> addAccessory(
            @PathVariable Long vehicleId, @PathVariable Long accessoryId) {
        return ResponseEntity.ok(vehicleService.addAccessory(vehicleId, accessoryId));
    }

    @DeleteMapping("/{vehicleId}/accessories/{accessoryId}")
    public ResponseEntity<VehicleResponse> removeAccessory(
            @PathVariable Long vehicleId, @PathVariable Long accessoryId) {
        return ResponseEntity.ok(vehicleService.removeAccessory(vehicleId, accessoryId));
    }

    // Replaces all mappings with the given ids; an empty list clears them.
    @PutMapping("/{vehicleId}/accessories")
    public ResponseEntity<VehicleResponse> replaceAccessories(
            @PathVariable Long vehicleId, @Valid @RequestBody AccessoryMappingRequest request) {
        return ResponseEntity.ok(vehicleService.replaceAccessories(vehicleId, request.accessoryIds()));
    }
}
