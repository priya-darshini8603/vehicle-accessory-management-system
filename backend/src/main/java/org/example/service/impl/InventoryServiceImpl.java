package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.InventoryItemReport;
import org.example.dto.InventoryReport;
import org.example.dto.PageResponse;
import org.example.dto.StockMovementRequest;
import org.example.dto.StockMovementResponse;
import org.example.entity.Accessory;
import org.example.enums.StockMovementType;
import org.example.exception.BusinessRuleException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.InventoryMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.StockMovementRepository;
import org.example.service.InventoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private static final int MIN_ANALYSIS_DAYS = 1;
    private static final int MAX_ANALYSIS_DAYS = 365;

    private final AccessoryRepository accessoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryMapper inventoryMapper;
    private final Clock clock;

    @Override
    @Transactional
    public StockMovementResponse recordMovement(
            Long accessoryId,
            StockMovementRequest request,
            String actor) {

        validateAdjustmentRequest(request);

        Accessory accessory = accessoryRepository.findByIdForUpdate(accessoryId)
                .orElseThrow(() ->
                        ResourceNotFoundException.of(
                                "Accessory",
                                accessoryId));

        int stockBefore = requireNonNegativeStock(accessory);

        long newStockValue =
                (long) stockBefore + request.quantityDelta();

        validateStockRange(newStockValue);

        int stockAfter = (int) newStockValue;

        accessory.setStockQuantity(stockAfter);

        return inventoryMapper.toResponse(
                stockMovementRepository.saveAndFlush(
                        inventoryMapper.toMovement(
                                accessory,
                                request.type(),
                                request.quantityDelta(),
                                stockBefore,
                                stockAfter,
                                request.note(),
                                actor
                        )
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getMovementHistory(
            Long accessoryId,
            StockMovementType type,
            Instant from,
            Instant to,
            Pageable pageable) {

        validateMovementHistoryRequest(
                accessoryId,
                from,
                to);

        return PageResponse.from(
                stockMovementRepository.findHistory(
                        accessoryId,
                        type,
                        from,
                        to,
                        pageable),
                inventoryMapper::toResponse
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryReport generateReport(int analysisPeriodDays) {

        validateAnalysisPeriod(analysisPeriodDays);

        Instant generatedAt = clock.instant();

        Instant since = generatedAt.minusSeconds(
                analysisPeriodDays * 24L * 60L * 60L);

        Map<Long, Long> salesByAccessory =
                loadSalesData(since);

        List<InventoryItemReport> products =
                buildInventoryReports(
                        salesByAccessory,
                        analysisPeriodDays);

        List<InventoryItemReport> lowStockItems =
                products.stream()
                        .filter(InventoryItemReport::lowStock)
                        .sorted(Comparator.comparingInt(
                                InventoryItemReport::currentStock))
                        .toList();

        List<InventoryItemReport> outOfStockItems =
                products.stream()
                        .filter(InventoryItemReport::outOfStock)
                        .toList();

        List<InventoryItemReport> fastMovingProducts =
                products.stream()
                        .filter(item -> item.unitsSold() > 0)
                        .sorted(
                                Comparator.comparingLong(
                                                InventoryItemReport::unitsSold)
                                        .reversed()
                                        .thenComparing(
                                                InventoryItemReport::name))
                        .toList();

        List<InventoryItemReport> slowMovingProducts =
                products.stream()
                        .sorted(
                                Comparator.comparingLong(
                                                InventoryItemReport::unitsSold)
                                        .thenComparing(
                                                InventoryItemReport::name))
                        .toList();

        List<InventoryItemReport> recentlyUpdatedProducts =
                products.stream()
                        .filter(item -> item.updatedAt() != null)
                        .sorted(Comparator.comparing(
                                        InventoryItemReport::updatedAt,
                                        Comparator.reverseOrder())
                                .thenComparing(InventoryItemReport::name))
                        .toList();

        List<InventoryItemReport> productsRequiringReorder =
                products.stream()
                        .filter(item -> item.recommendedReorderQuantity() > 0)
                        .sorted(Comparator.comparing(
                                        InventoryItemReport::stockCoverDays,
                                        Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparing(InventoryItemReport::name))
                        .toList();

        List<StockMovementResponse> recentMovements =
                stockMovementRepository
                        .findTop20ByOrderByOccurredAtDescIdDesc()
                        .stream()
                        .map(inventoryMapper::toResponse)
                        .toList();

        return new InventoryReport(
                generatedAt,
                analysisPeriodDays,
                products,
                lowStockItems,
                outOfStockItems,
                topQuartile(fastMovingProducts),
                topQuartile(slowMovingProducts),
                topQuartile(recentlyUpdatedProducts),
                productsRequiringReorder,
                recentMovements
        );
    }

    private void validateAdjustmentRequest(
            StockMovementRequest request) {

        if (request == null
                || request.type() == null
                || request.quantityDelta() == null
                || !request.isValidMovement()) {

            throw new BusinessRuleException(
                    "Stock movement type and quantity delta are invalid");
        }

        if (request.type() != StockMovementType.ADJUSTMENT) {
            throw new BusinessRuleException(
                    "Only inventory adjustments can be recorded directly");
        }

        if (request.note() == null
                || request.note().isBlank()) {

            throw new BusinessRuleException(
                    "A reason is required for stock adjustments");
        }
    }

    private void validateMovementHistoryRequest(
            Long accessoryId,
            Instant from,
            Instant to) {

        if (!accessoryRepository.existsById(accessoryId)) {
            throw ResourceNotFoundException.of(
                    "Accessory",
                    accessoryId);
        }

        if (from != null
                && to != null
                && from.isAfter(to)) {

            throw new BusinessRuleException(
                    "Movement start date must not be after end date");
        }
    }

    private void validateAnalysisPeriod(
            int analysisPeriodDays) {

        if (analysisPeriodDays < MIN_ANALYSIS_DAYS
                || analysisPeriodDays > MAX_ANALYSIS_DAYS) {

            throw new BusinessRuleException(
                    "Analysis period must be between "
                            + MIN_ANALYSIS_DAYS
                            + " and "
                            + MAX_ANALYSIS_DAYS
                            + " days");
        }
    }

    private Map<Long, Long> loadSalesData(
            Instant since) {

        Map<Long, Long> salesByAccessory = new HashMap<>();

        stockMovementRepository
                .sumSalesSince(
                        StockMovementType.SALE,
                        since)
                .forEach(result ->
                        salesByAccessory.put(
                                result.getAccessoryId(),
                        result.getUnitsSold() == null
                                ? 0L
                                : Math.max(0L, result.getUnitsSold())));

        return salesByAccessory;
    }

    private List<InventoryItemReport> buildInventoryReports(
            Map<Long, Long> salesByAccessory,
            int analysisPeriodDays) {

        return accessoryRepository.findAll()
                .stream()
                .map(accessory ->
                        inventoryMapper.toInventoryItemReport(
                                accessory,
                                requireNonNegativeStock(accessory),
                                salesByAccessory.getOrDefault(
                                        accessory.getId(),
                                        0L),
                                analysisPeriodDays))
                .toList();
    }

    private static List<InventoryItemReport> topQuartile(
            List<InventoryItemReport> reports) {

        if (reports.isEmpty()) {
            return List.of();
        }

        int size = Math.max(
                1,
                (reports.size() + 3) / 4);

        return List.copyOf(
                reports.subList(0, size));
    }

    private static int requireNonNegativeStock(
            Accessory accessory) {

        Integer stock = accessory.getStockQuantity();

        if (stock == null || stock < 0) {
            throw new IllegalStateException(
                    "Accessory "
                            + accessory.getId()
                            + " has an invalid stock quantity");
        }

        return stock;
    }

    private static void validateStockRange(
            long stockValue) {

        if (stockValue < 0) {
            throw new BusinessRuleException(
                    "Stock cannot become negative");
        }

        if (stockValue > Integer.MAX_VALUE) {
            throw new BusinessRuleException(
                    "Stock quantity exceeds the supported maximum");
        }
    }
}