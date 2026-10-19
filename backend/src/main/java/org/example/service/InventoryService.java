package org.example.service;

import org.example.dto.InventoryReport;
import org.example.dto.PageResponse;
import org.example.dto.StockMovementRequest;
import org.example.dto.StockMovementResponse;
import org.springframework.data.domain.Pageable;

public interface InventoryService {

    StockMovementResponse recordMovement(Long accessoryId, StockMovementRequest request, String actor);

    PageResponse<StockMovementResponse> getMovementHistory(Long accessoryId, Pageable pageable);

    InventoryReport generateReport(int analysisPeriodDays);
}
