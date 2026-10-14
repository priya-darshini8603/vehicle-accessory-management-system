
package org.example.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.mapper.AccessoryMapper;
import org.example.service.AccessoryService;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/accessories")
@RequiredArgsConstructor
@Slf4j
public class AccessoryController {

    // Handles accessory storage and updates.
    private final AccessoryService accessoryService;

    // Converts accessory data to and from the API.
    private final AccessoryMapper accessoryMapper;

    // Adds one accessory item.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccessoryResponse> addAccessory(
            @Valid @RequestBody AccessoryRequest request) {
        log.debug("Received request to add an accessory");
        AccessoryResponse response = accessoryMapper.toResponse(
                accessoryService.addAccessory(accessoryMapper.toEntity(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Lists all accessory items.
    @GetMapping
    public ResponseEntity<List<AccessoryResponse>> getAllAccessories() {
        log.debug("Received request to list accessories");
        return ResponseEntity.ok(accessoryMapper.toResponses(accessoryService.getAllAccessories()));
    }

    // Gets one accessory by Id.
    @GetMapping("/{id}")
    public ResponseEntity<AccessoryResponse> getAccessoryById(@PathVariable Long id) {
        log.debug("Received request to retrieve accessory with id {}", id);
        return ResponseEntity.ok(accessoryMapper.toResponse(accessoryService.getAccessoryById(id)));
    }

    // Updates an accessory item.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccessoryResponse> updateAccessory(
            @PathVariable Long id,
            @Valid @RequestBody AccessoryRequest request) {
        log.debug("Received request to update accessory with id {}", id);
        AccessoryResponse response = accessoryMapper.toResponse(
                accessoryService.updateAccessory(id, accessoryMapper.toEntity(request)));
        return ResponseEntity.ok(response);
    }

    // Deletes an accessory item.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteAccessory(@PathVariable Long id) {
        log.debug("Received request to delete accessory with id {}", id);
        accessoryService.deleteAccessory(id);
        return ResponseEntity.ok("Accessory deleted successfully.");
    }
}
