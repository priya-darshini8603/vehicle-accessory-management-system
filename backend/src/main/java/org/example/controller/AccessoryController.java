
package org.example.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.mapper.AccessoryMapper;
import org.example.service.AccessoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accessories")
@RequiredArgsConstructor
public class AccessoryController {

    /**
     * Business service used to manage inventory items for the store.
     */
    private final AccessoryService accessoryService;

    /**
     * Mapper that converts between DTOs and entity objects for clean API contracts.
     */
    private final AccessoryMapper accessoryMapper;

    /**
     * Creates a new accessory record from the incoming request payload.
     */
    @PostMapping
    public ResponseEntity<AccessoryResponse> addAccessory(
            @Valid @RequestBody AccessoryRequest request) {
        AccessoryResponse response = accessoryMapper.toResponse(
                accessoryService.addAccessory(accessoryMapper.toEntity(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the full inventory list for the accessory catalog.
     */
    @GetMapping
    public ResponseEntity<List<AccessoryResponse>> getAllAccessories() {
        return ResponseEntity.ok(accessoryMapper.toResponses(accessoryService.getAllAccessories()));
    }

    /**
     * Fetches one accessory by its identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccessoryResponse> getAccessoryById(@PathVariable Long id) {
        return ResponseEntity.ok(accessoryMapper.toResponse(accessoryService.getAccessoryById(id)));
    }

    /**
     * Updates an existing accessory using the supplied patch-like payload.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AccessoryResponse> updateAccessory(
            @PathVariable Long id,
            @Valid @RequestBody AccessoryRequest request) {
        AccessoryResponse response = accessoryMapper.toResponse(
                accessoryService.updateAccessory(id, accessoryMapper.toEntity(request)));
        return ResponseEntity.ok(response);
    }

    /**
     * Removes a stored accessory when it is not assigned to any vehicle.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccessory(@PathVariable Long id) {
        accessoryService.deleteAccessory(id);
        return ResponseEntity.noContent().build();
    }
}
