package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.CompatibilityResponse;
import org.example.dto.GarageVehicleRequest;
import org.example.dto.GarageVehicleResponse;
import org.example.dto.GarageVehicleUpdateRequest;
import org.example.service.GarageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// The caller's own vehicles. The owner always comes from the token, never from the request.
@RestController
@RequestMapping("/api/me/vehicles")
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
@RequiredArgsConstructor
public class GarageController {

    private final GarageService garageService;

    // Primary vehicle first, then in the order they were added. Not paged: a garage is capped in size.
    @GetMapping
    public ResponseEntity<List<GarageVehicleResponse>> getGarage(@AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(garageService.getGarage(currentUser.getUsername()));
    }

    @PostMapping
    public ResponseEntity<GarageVehicleResponse> addVehicle(
            @Valid @RequestBody GarageVehicleRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        GarageVehicleResponse created = garageService.addVehicle(currentUser.getUsername(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GarageVehicleResponse> getVehicle(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(garageService.getVehicle(currentUser.getUsername(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GarageVehicleResponse> updateVehicle(
            @PathVariable Long id,
            @Valid @RequestBody GarageVehicleUpdateRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(garageService.updateVehicle(currentUser.getUsername(), id, request));
    }

    // Idempotent: making the primary vehicle primary again changes nothing.
    @PutMapping("/{id}/primary")
    public ResponseEntity<GarageVehicleResponse> makePrimary(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(garageService.makePrimary(currentUser.getUsername(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeVehicle(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails currentUser) {
        garageService.removeVehicle(currentUser.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/accessories")
    public ResponseEntity<CompatibilityResponse> getCompatibleAccessories(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(garageService.getCompatibleAccessories(currentUser.getUsername(), id));
    }
}
