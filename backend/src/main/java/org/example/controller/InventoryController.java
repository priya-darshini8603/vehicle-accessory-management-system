package org.example.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.example.dto.InventoryReport;
import org.example.dto.PageResponse;
import org.example.dto.StockMovementRequest;
import org.example.dto.StockMovementResponse;
import org.example.service.InventoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/accessories/{accessoryId}/movements")
    public ResponseEntity<StockMovementResponse> recordMovement(
            @PathVariable Long accessoryId,
            @Valid @RequestBody StockMovementRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(inventoryService.recordMovement(accessoryId, request, currentUser.getUsername()));
    }

    @GetMapping("/accessories/{accessoryId}/movements")
    public ResponseEntity<PageResponse<StockMovementResponse>> getMovementHistory(
            @PathVariable Long accessoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getMovementHistory(accessoryId, pageable));
    }

    @GetMapping("/report")
    public ResponseEntity<InventoryReport> getReport(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return ResponseEntity.ok(inventoryService.generateReport(days));
    }
}
