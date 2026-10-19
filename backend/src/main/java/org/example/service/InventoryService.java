package org.example.service;

import org.example.dto.InventoryReport;
import org.example.dto.PageResponse;
import org.example.dto.StockMovementRequest;
import org.example.dto.StockMovementResponse;
import org.example.enums.StockMovementType;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface InventoryService {

    StockMovementResponse recordMovement(Long accessoryId, StockMovementRequest request, String actor);

    PageResponse<StockMovementResponse> getMovementHistory(Long accessoryId, StockMovementType type,
                                                           Instant from, Instant to, Pageable pageable);

    InventoryReport generateReport(int analysisPeriodDays);
}
