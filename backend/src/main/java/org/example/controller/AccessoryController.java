
package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.dto.PageResponse;
import org.example.service.AccessoryService;
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
@RequestMapping("/api/admin/accessories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AccessoryController {

    /**
     * Business service used to manage inventory items for the store.
     */
    private final AccessoryService accessoryService;

    /**
     * Creates a new accessory record from the incoming request payload.
     */
    @PostMapping
    public ResponseEntity<AccessoryResponse> createAccessory(@Valid @RequestBody AccessoryRequest request) {
        AccessoryResponse created = accessoryService.createAccessory(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Returns the accessory catalog, paged. Supports ?page=0&size=20&sort=name,asc
     */
    @GetMapping
    public ResponseEntity<PageResponse<AccessoryResponse>> getAccessories(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(accessoryService.getAccessories(pageable));
    }

    /**
     * Fetches one accessory by its identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccessoryResponse> getAccessoryById(@PathVariable Long id) {
        return ResponseEntity.ok(accessoryService.getAccessoryById(id));
    }

    /**
     * Replaces all editable fields of an existing accessory.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AccessoryResponse> updateAccessory(
            @PathVariable Long id,
            @Valid @RequestBody AccessoryRequest request) {
        return ResponseEntity.ok(accessoryService.updateAccessory(id, request));
    }

    /**
     * Deletes an accessory, unmapping it from any vehicles first.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccessory(@PathVariable Long id) {
        accessoryService.deleteAccessory(id);
        return ResponseEntity.noContent().build();
    }
}
