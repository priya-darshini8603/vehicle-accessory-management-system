package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.InventoryItemReport;
import org.example.dto.InventoryReport;
import org.example.dto.PageResponse;
import org.example.dto.StockMovementRequest;
import org.example.dto.StockMovementResponse;
import org.example.entity.Accessory;
import org.example.entity.StockMovement;
import org.example.enums.StockMovementType;
import org.example.exception.BusinessRuleException;
import org.example.exception.ResourceNotFoundException;
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

    private final AccessoryRepository accessoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    @Override
    @Transactional
    public StockMovementResponse recordMovement(Long accessoryId, StockMovementRequest request, String actor) {
        if (request == null || request.type() == null || request.quantityDelta() == null
                || !request.isValidMovement()) {
            throw new BusinessRuleException("Stock movement type and quantity delta are invalid");
        }
        if (request.type() != StockMovementType.SALE
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessRuleException("A reason is required for receipts, returns, and stock adjustments");
        }
        Accessory accessory = accessoryRepository.findByIdForUpdate(accessoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Accessory", accessoryId));
        int before = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
        long newStock = (long) before + request.quantityDelta();
        if (newStock < 0) {
            throw new BusinessRuleException("Stock cannot become negative");
        }
        if (newStock > Integer.MAX_VALUE) {
            throw new BusinessRuleException("Stock quantity exceeds the supported maximum");
        }

        accessory.setStockQuantity((int) newStock);
        return toResponse(stockMovementRepository.saveAndFlush(createMovement(
                accessory, request.type(), request.quantityDelta(), before, (int) newStock,
                request.note(), actor)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getMovementHistory(Long accessoryId, Pageable pageable) {
        if (!accessoryRepository.existsById(accessoryId)) {
            throw ResourceNotFoundException.of("Accessory", accessoryId);
        }
        return PageResponse.from(
                stockMovementRepository.findByAccessoryIdOrderByOccurredAtDescIdDesc(accessoryId, pageable),
                InventoryServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryReport generateReport(int analysisPeriodDays) {
        if (analysisPeriodDays < 1 || analysisPeriodDays > 365) {
            throw new BusinessRuleException("Analysis period must be between 1 and 365 days");
        }
        Instant now = clock.instant();
        Instant since = now.minusSeconds(analysisPeriodDays * 24L * 60 * 60);
        Map<Long, Long> salesByAccessory = new HashMap<>();
        stockMovementRepository.sumSalesSince(StockMovementType.SALE, since)
                .forEach(sale -> salesByAccessory.put(sale.getAccessoryId(), sale.getUnitsSold()));

        List<InventoryItemReport> products = accessoryRepository.findAll().stream()
                .map(accessory -> {
                    long sold = salesByAccessory.getOrDefault(accessory.getId(), 0L);
                    int stock = Math.max(0, accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity());
                    int minimum = accessory.getMinStockThreshold() == null ? 0 : accessory.getMinStockThreshold();
                    int leadTime = Math.max(0, accessory.getLeadTimeDays() == null ? 0 : accessory.getLeadTimeDays());
                    double averageDailySales = (double) sold / analysisPeriodDays;
                    double leadTimeDemand = averageDailySales * leadTime;
                    double reorderPoint = leadTimeDemand + minimum;
                    double stockCoverDays = averageDailySales > 0 ? stock / averageDailySales : Double.NaN;
                    double replenishment = Math.max(0, Math.ceil(reorderPoint - stock));
                    int recommended = (int) Math.min(Integer.MAX_VALUE, replenishment);
                    return new InventoryItemReport(accessory.getId(), accessory.getName(), accessory.getCategory(),
                            stock, minimum, leadTime, sold, averageDailySales, leadTimeDemand, reorderPoint,
                            Double.isNaN(stockCoverDays) ? null : stockCoverDays,
                            stock > 0 && stock <= minimum, stock == 0, recommended);
                })
                .toList();

        List<InventoryItemReport> lowStock = products.stream()
                .filter(InventoryItemReport::lowStock)
                .sorted(Comparator.comparingInt(InventoryItemReport::currentStock))
                .toList();
        List<InventoryItemReport> outOfStock = products.stream()
                .filter(InventoryItemReport::outOfStock)
                .toList();

        List<InventoryItemReport> fastest = products.stream()
                .filter(item -> item.unitsSold() > 0)
                .sorted(Comparator.comparingLong(InventoryItemReport::unitsSold).reversed()
                        .thenComparing(InventoryItemReport::name))
                .toList();
        List<InventoryItemReport> slowest = products.stream()
                .sorted(Comparator.comparingLong(InventoryItemReport::unitsSold)
                        .thenComparing(InventoryItemReport::name))
                .toList();

        List<StockMovementResponse> recentMovements = stockMovementRepository
                .findTop20ByOrderByOccurredAtDescIdDesc().stream()
                .map(InventoryServiceImpl::toResponse)
                .toList();
        return new InventoryReport(now, analysisPeriodDays, products, lowStock, outOfStock,
                quartile(fastest), quartile(slowest), recentMovements);
    }

    private static List<InventoryItemReport> quartile(List<InventoryItemReport> ranked) {
        if (ranked.isEmpty()) {
            return List.of();
        }
        return List.copyOf(ranked.subList(0, Math.max(1, (ranked.size() + 3) / 4)));
    }

    public static StockMovement createMovement(Accessory accessory, StockMovementType type, int delta,
                                               int before, int after, String note, String actor) {
        StockMovement movement = new StockMovement();
        movement.setAccessory(accessory);
        movement.setType(type);
        movement.setQuantityDelta(delta);
        movement.setStockBefore(before);
        movement.setStockAfter(after);
        movement.setNote(note);
        movement.setChangedBy(actor);
        return movement;
    }

    private static StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getAccessory().getId(),
                movement.getAccessory().getName(),
                movement.getType(),
                movement.getQuantityDelta(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getNote(),
                movement.getChangedBy(),
                movement.getOccurredAt());
    }
}
