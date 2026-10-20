package org.example.service.impl;

import org.example.dto.InventoryItemReport;
import org.example.dto.InventoryReport;
import org.example.dto.StockMovementRequest;
import org.example.entity.Accessory;
import org.example.enums.StockMovementType;
import org.example.exception.BusinessRuleException;
import org.example.mapper.InventoryMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private AccessoryRepository accessoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(accessoryRepository, stockMovementRepository,
                new InventoryMapper(),
                Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void adjustmentRequiresAReason() {
        assertThatThrownBy(() -> service.recordMovement(1L,
                new StockMovementRequest(StockMovementType.ADJUSTMENT, 2, "  "), "admin@test.local"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A reason is required for stock adjustments");

        verify(stockMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void adjustmentLocksAccessoryUpdatesStockAndRecordsBeforeAndAfterQuantities() {
        Accessory accessory = accessory(1L, 8, 2, 5);
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(accessory));
        when(stockMovementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.recordMovement(1L,
                new StockMovementRequest(StockMovementType.ADJUSTMENT, -3, "Physical count"), "admin@test.local");

        assertThat(accessory.getStockQuantity()).isEqualTo(5);
        assertThat(response.type()).isEqualTo(StockMovementType.ADJUSTMENT);
        assertThat(response.quantityDelta()).isEqualTo(-3);
        assertThat(response.stockBefore()).isEqualTo(8);
        assertThat(response.stockAfter()).isEqualTo(5);
        assertThat(response.changedBy()).isEqualTo("admin@test.local");
    }

    @Test
    void directSaleOrRestorationMovementIsRejected() {
        assertThatThrownBy(() -> service.recordMovement(1L,
                new StockMovementRequest(StockMovementType.SALE, -1, "Order 17"), "admin@test.local"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only inventory adjustments can be recorded directly");
        assertThatThrownBy(() -> service.recordMovement(1L,
                new StockMovementRequest(StockMovementType.RESTORATION, 1, "Order 17"), "admin@test.local"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only inventory adjustments can be recorded directly");
        verify(accessoryRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void movementThatWouldMakeStockNegativeIsRejectedWithoutWritingHistory() {
        Accessory accessory = accessory(1L, 2, 0, 7);
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(accessory));

        assertThatThrownBy(() -> service.recordMovement(1L,
                new StockMovementRequest(StockMovementType.ADJUSTMENT, -3, "Physical count"), "admin@test.local"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Stock cannot become negative");

        assertThat(accessory.getStockQuantity()).isEqualTo(2);
        verify(stockMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportCalculatesReorderRecommendationsAndStockHealthFromSalesHistory() {
        Accessory fastSeller = accessory(1L, 8, 10, 10);
        Accessory outOfStock = accessory(2L, 0, 2, 7);
        when(accessoryRepository.findAll()).thenReturn(List.of(fastSeller, outOfStock));
        when(stockMovementRepository.sumSalesSince(any(), any())).thenReturn(List.of(
                sales(1L, 30L)));

        InventoryReport report = service.generateReport(30);

        InventoryItemReport fastItem = report.products().getFirst();
        assertThat(fastItem.averageDailySales()).isEqualByComparingTo("1.0");
        assertThat(fastItem.leadTimeDemand()).isEqualByComparingTo("10.0");
        assertThat(fastItem.reorderPoint()).isEqualByComparingTo("20.0");
        assertThat(fastItem.stockCoverDays()).isEqualByComparingTo("8.0");
        assertThat(fastItem.recommendedReorderQuantity()).isEqualTo(12);
        assertThat(report.lowStockItems()).containsExactly(fastItem);
        assertThat(report.outOfStockItems()).extracting(InventoryItemReport::accessoryId).containsExactly(2L);
        assertThat(report.fastMovingProducts()).containsExactly(fastItem);
        assertThat(report.slowMovingProducts()).hasSize(1);
        assertThat(report.productsRequiringReorder()).extracting(InventoryItemReport::accessoryId)
                .containsExactly(2L, 1L);
        verify(stockMovementRepository, times(1)).sumSalesSince(StockMovementType.SALE,
                Instant.parse("2026-09-19T00:00:00Z"));
        verify(accessoryRepository, times(1)).findAll();
        verify(accessoryRepository, never()).save(any());
    }

    @Test
    void reportHandlesNewAccessoriesAndZeroSalesWithoutInventingStockCover() {
        Accessory newAccessory = accessory(1L, 6, 4, 7);
        Accessory emptyAccessory = accessory(2L, 0, 2, 3);
        when(accessoryRepository.findAll()).thenReturn(List.of(newAccessory, emptyAccessory));
        when(stockMovementRepository.sumSalesSince(any(), any())).thenReturn(List.of());

        InventoryReport report = service.generateReport(30);

        assertThat(report.products()).allSatisfy(item -> {
            assertThat(item.unitsSold()).isZero();
            assertThat(item.averageDailySales()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.leadTimeDemand()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.stockCoverDays()).isNull();
        });
        assertThat(report.products()).extracting(InventoryItemReport::recommendedReorderQuantity)
                .containsExactly(0, 2);
        assertThat(report.products()).extracting(InventoryItemReport::outOfStock)
                .containsExactly(false, true);
    }

    @Test
    void reportIncludesSafetyStockInReorderRecommendation() {
        Accessory accessory = accessory(1L, 10, 5, 7);
        when(accessoryRepository.findAll()).thenReturn(List.of(accessory));
        when(stockMovementRepository.sumSalesSince(any(), any())).thenReturn(List.of(sales(1L, 60L)));

        InventoryReport report = service.generateReport(30);
        InventoryItemReport item = report.products().getFirst();

        assertThat(item.unitsSold()).isEqualTo(60);
        assertThat(item.averageDailySales()).isEqualByComparingTo("2.0");
        assertThat(item.leadTimeDemand()).isEqualByComparingTo("14.0");
        assertThat(item.reorderPoint()).isEqualByComparingTo("19.0");
        assertThat(item.stockCoverDays()).isEqualByComparingTo("5.0");
        assertThat(item.lowStock()).isTrue();
        assertThat(item.outOfStock()).isFalse();
        assertThat(item.recommendedReorderQuantity()).isEqualTo(9);
    }

    @Test
    void reportTreatsNullMinimumAndLeadTimeAsZeroAndPreventsNegativeRecommendations() {
        Accessory nullMinimum = accessory(1L, 5, 10, 10);
        nullMinimum.setMinStockThreshold(null);
        Accessory nullLeadTime = accessory(2L, 50, 10, 10);
        nullLeadTime.setLeadTimeDays(null);
        when(accessoryRepository.findAll()).thenReturn(List.of(nullMinimum, nullLeadTime));
        when(stockMovementRepository.sumSalesSince(any(), any())).thenReturn(List.of(
                sales(1L, 60L), sales(2L, 60L)));

        InventoryReport report = service.generateReport(30);

        assertThat(report.products()).extracting(InventoryItemReport::recommendedReorderQuantity)
                .containsExactly(15, 0);
        assertThat(report.products()).extracting(InventoryItemReport::minimumStock)
                .containsExactly(0, 10);
        assertThat(report.products()).extracting(InventoryItemReport::leadTimeDays)
                .containsExactly(10, 0);
    }

    @Test
    void reportSortsFastSlowAndRecentlyUpdatedSectionsAndMarksReorderStatus() {
        Accessory slowSeller = accessory(1L, 100, 5, 7);
        slowSeller.setUpdatedAt(Instant.parse("2026-10-18T00:00:00Z"));
        Accessory fastSeller = accessory(2L, 1, 5, 7);
        fastSeller.setUpdatedAt(Instant.parse("2026-10-19T00:00:00Z"));
        Accessory healthy = accessory(3L, 100, 0, 7);
        healthy.setUpdatedAt(Instant.parse("2026-10-17T00:00:00Z"));
        when(accessoryRepository.findAll()).thenReturn(List.of(slowSeller, fastSeller, healthy));
        when(stockMovementRepository.sumSalesSince(any(), any())).thenReturn(List.of(
                sales(1L, 1L), sales(2L, 90L), sales(3L, 0L)));

        InventoryReport report = service.generateReport(30);

        assertThat(report.fastMovingProducts()).extracting(InventoryItemReport::accessoryId)
                .containsExactly(2L);
        assertThat(report.slowMovingProducts()).extracting(InventoryItemReport::accessoryId)
                .containsExactly(3L);
        assertThat(report.recentlyUpdatedProducts()).extracting(InventoryItemReport::accessoryId)
                .containsExactly(2L);
        assertThat(report.productsRequiringReorder()).extracting(InventoryItemReport::accessoryId)
                .containsExactly(2L);
        assertThat(report.products().get(1).lowStock()).isTrue();
        assertThat(report.products().get(2).lowStock()).isFalse();
    }

    private static Accessory accessory(Long id, int stock, int minimum, int leadTime) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setName("Accessory " + id);
        accessory.setCategory("Interior");
        accessory.setStockQuantity(stock);
        accessory.setMinStockThreshold(minimum);
        accessory.setLeadTimeDays(leadTime);
        return accessory;
    }

    private static StockMovementRepository.SalesAggregate sales(Long accessoryId, Long unitsSold) {
        return new StockMovementRepository.SalesAggregate() {
            @Override
            public Long getAccessoryId() {
                return accessoryId;
            }

            @Override
            public Long getUnitsSold() {
                return unitsSold;
            }
        };
    }
}
