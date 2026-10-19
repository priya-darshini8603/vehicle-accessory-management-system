package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.InventoryReport;
import org.example.service.InventoryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryAnalysisJob {

    private final InventoryService inventoryService;

    @Scheduled(cron = "${app.inventory.analysis-cron:0 0 0 * * *}", zone = "${app.inventory.analysis-time-zone:UTC}")
    public void analyzeInventory() {
        InventoryReport report = inventoryService.generateReport(30);
        log.info("Daily inventory report generated: {} products, {} low-stock, {} out-of-stock",
                report.products().size(), report.lowStockItems().size(), report.outOfStockItems().size());
        report.lowStockItems().forEach(item ->
                log.warn("Low stock alert: accessory {} ({}) has {} units; minimum is {}",
                        item.accessoryId(), item.name(), item.currentStock(), item.minimumStock()));
        report.outOfStockItems().forEach(item ->
                log.warn("Out-of-stock alert: accessory {} ({})", item.accessoryId(), item.name()));
    }
}
