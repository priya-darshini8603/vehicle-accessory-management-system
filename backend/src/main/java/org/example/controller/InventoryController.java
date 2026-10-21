package org.example.controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.example.dto.InventoryReport;
import org.example.dto.PageResponse;
import org.example.dto.StockMovementRequest;
import org.example.dto.StockMovementResponse;
import org.example.enums.StockMovementType;
import org.example.service.InventoryService;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Records a stock movement for an accessory.
     *
     * POST /api/admin/inventory/accessories/{accessoryId}/movements
     */
    @PostMapping("/accessories/{accessoryId}/movements")
    public ResponseEntity<StockMovementResponse> recordMovement(
            @PathVariable Long accessoryId,
            @Valid @RequestBody StockMovementRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        StockMovementResponse response =
                inventoryService.recordMovement(
                        accessoryId,
                        request,
                        currentUser.getUsername()
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves paginated stock movement history.
     *
     * GET /api/admin/inventory/accessories/{accessoryId}/movements
     *
     * Optional filters:
     * type - movement type
     * from - inclusive start timestamp
     * to   - inclusive end timestamp
     */
    @GetMapping("/accessories/{accessoryId}/movements")
    public ResponseEntity<PageResponse<StockMovementResponse>>
    getMovementHistory(
            @PathVariable Long accessoryId,

            @RequestParam(required = false)
            StockMovementType type,

            @RequestParam(required = false)
            Instant from,

            @RequestParam(required = false)
            Instant to,

            @PageableDefault(
                    size = 20,
                    sort = {"occurredAt", "id"},
                    direction = Sort.Direction.DESC
            )
            Pageable pageable) {

        PageResponse<StockMovementResponse> response =
                inventoryService.getMovementHistory(
                        accessoryId,
                        type,
                        from,
                        to,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Generates an inventory report.
     *
     * GET /api/admin/inventory/report?days=30 days must be between 1 and 365.
     */
    @GetMapping("/report")
    public ResponseEntity<InventoryReport> getReport(
            @RequestParam(defaultValue = "30")
            @Min(value = 1, message = "days must be at least 1")
            @Max(value = 365, message = "days must not exceed 365")
            int days) {

        InventoryReport report =
                inventoryService.generateReport(days);

        return ResponseEntity.ok(report);
    }
}
