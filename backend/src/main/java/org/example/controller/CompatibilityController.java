package org.example.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.dto.CompatibilityResponse;
import org.example.service.CompatibilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Accessory compatibility search for customers and admins. Vehicle-number lookups are matched
// trimmed and case-insensitively; model lookups return every matching vehicle.
@RestController
@RequestMapping("/api/compatibility")
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
@RequiredArgsConstructor
public class CompatibilityController {

    private final CompatibilityService compatibilityService;

    // GET /api/compatibility?vehicleNumber=KA01AB1234
    @GetMapping
    public ResponseEntity<CompatibilityResponse> searchByQuery(
            @RequestParam
            @NotBlank(message = "vehicleNumber must not be blank")
            @Size(max = 64, message = "vehicleNumber must not exceed 64 characters")
            String vehicleNumber) {
        return ResponseEntity.ok(compatibilityService.findByVehicleNumber(vehicleNumber));
    }

    // GET /api/compatibility/vehicles/KA01AB1234/accessories
    @GetMapping("/vehicles/{vehicleNumber}/accessories")
    public ResponseEntity<CompatibilityResponse> searchByPath(
            @PathVariable
            @Size(max = 64, message = "vehicleNumber must not exceed 64 characters")
            String vehicleNumber) {
        return ResponseEntity.ok(compatibilityService.findByVehicleNumber(vehicleNumber));
    }

    // GET /api/compatibility/models?name=Swift
    @GetMapping("/models")
    public ResponseEntity<List<CompatibilityResponse>> searchByModelName(
            @RequestParam
            @NotBlank(message = "name must not be blank")
            @Size(max = 60, message = "name must not exceed 60 characters")
            String name) {
        return ResponseEntity.ok(compatibilityService.findByModelName(name));
    }
}
