package org.example.service.impl;

import org.example.dto.InventoryItemReport;
import org.example.dto.InventoryReport;
import org.example.dto.StockMovementRequest;
import org.example.entity.Accessory;
import org.example.enums.StockMovementType;
import org.example.exception.BusinessRuleException;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
                Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void saleLocksAccessoryUpdatesStockAndRecordsBeforeAndAfterQuantities() {
        Accessory accessory = accessory(1L, 8, 2, 5);
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(accessory));
        when(stockMovementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.recordMovement(1L,
                new StockMovementRequest(StockMovementType.SALE, -3, "Order 17"), "admin@test.local");

        assertThat(accessory.getStockQuantity()).isEqualTo(5);
        assertThat(response.type()).isEqualTo(StockMovementType.SALE);
        assertThat(response.quantityDelta()).isEqualTo(-3);
        assertThat(response.stockBefore()).isEqualTo(8);
        assertThat(response.stockAfter()).isEqualTo(5);
        assertThat(response.changedBy()).isEqualTo("admin@test.local");
    }

    @Test
    void movementThatWouldMakeStockNegativeIsRejectedWithoutWritingHistory() {
        Accessory accessory = accessory(1L, 2, 0, 7);
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(accessory));

        assertThatThrownBy(() -> service.recordMovement(1L,
                new StockMovementRequest(StockMovementType.SALE, -3, null), "admin@test.local"))
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
        assertThat(fastItem.averageDailySales()).isEqualTo(1.0);
        assertThat(fastItem.recommendedReorderQuantity()).isEqualTo(12);
        assertThat(report.lowStockItems()).containsExactly(fastItem);
        assertThat(report.outOfStockItems()).extracting(InventoryItemReport::accessoryId).containsExactly(2L);
        assertThat(report.fastMovingProducts()).containsExactly(fastItem);
        assertThat(report.slowMovingProducts()).hasSize(1);
    }

    @Test
    void reportIncludesSafetyStockInReorderRecommendation() {
        Accessory accessory = accessory(1L, 5, 10, 10);
        when(accessoryRepository.findAll()).thenReturn(List.of(accessory));
        when(stockMovementRepository.sumSalesSince(any(), any())).thenReturn(List.of(sales(1L, 60L)));

        InventoryReport report = service.generateReport(30);

        assertThat(report.products().getFirst().averageDailySales()).isEqualTo(2.0);
        assertThat(report.products().getFirst().recommendedReorderQuantity()).isEqualTo(25);
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
