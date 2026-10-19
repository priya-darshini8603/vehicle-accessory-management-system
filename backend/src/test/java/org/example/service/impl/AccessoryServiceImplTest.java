package org.example.service.impl;

import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.entity.Accessory;
import org.example.entity.PriceChange;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.AccessoryMapper;
import org.example.mapper.InventoryMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.DiscountRepository;
import org.example.repository.PriceChangeRepository;
import org.example.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessoryServiceImplTest {

    @Mock
    private AccessoryRepository accessoryRepository;

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private PriceChangeRepository priceChangeRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private AccessoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccessoryServiceImpl(accessoryRepository, new AccessoryMapper(), new InventoryMapper(),
                discountRepository, priceChangeRepository, stockMovementRepository);
    }

    @Test
    void updateAccessoryCopiesAllEditableFieldsIncludingDescription() {
        Accessory existing = new Accessory();
        existing.setId(1L);
        existing.setName("Old");
        existing.setCategory("Old");
        existing.setPrice(BigDecimal.ONE);
        existing.setDescription("old description");
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        AccessoryResponse response = service.updateAccessory(1L, new AccessoryRequest(
                "Floor Mats", "Interior", new BigDecimal("1499.00"), "All-weather rubber mats",
                25, null, null, null, null, "Physical count"),
                "admin@test.local");

        assertThat(response.name()).isEqualTo("Floor Mats");
        assertThat(response.category()).isEqualTo("Interior");
        assertThat(response.price()).isEqualByComparingTo("1499.00");
        assertThat(response.description()).isEqualTo("All-weather rubber mats");
        assertThat(response.stockQuantity()).isEqualTo(25);
        verify(stockMovementRepository).save(any());
        verify(accessoryRepository).flush();
    }

    @Test
    void stockUpdateWithoutReasonIsRejected() {
        Accessory existing = accessory(1L, "1000.00", null);
        existing.setStockQuantity(5);
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateAccessory(1L, new AccessoryRequest(
                "Mats", "Interior", new BigDecimal("1000.00"), null, 8), "admin@test.local"))
                .isInstanceOf(org.example.exception.BusinessRuleException.class)
                .hasMessage("A reason is required when adjusting stock");

        assertThat(existing.getStockQuantity()).isEqualTo(5);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void negativeStockIsRejectedByTheService() {
        assertThatThrownBy(() -> service.updateAccessory(1L, new AccessoryRequest(
                "Mats", "Interior", new BigDecimal("1000.00"), null, -1), "admin@test.local"))
                .isInstanceOf(org.example.exception.BusinessRuleException.class)
                .hasMessage("Stock cannot become negative");

        verify(accessoryRepository, never()).findByIdForUpdate(1L);
    }

    @Test
    void priceChangeIsRecordedWithOldAndNewValuesAndActor() {
        Accessory existing = accessory(1L, "1000.00", "600.00");
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        service.updateAccessory(1L, new AccessoryRequest("Mats", "Interior", new BigDecimal("1200.00"),
                null, null, new BigDecimal("650.00"), null), "admin@test.local");

        ArgumentCaptor<PriceChange> captor = ArgumentCaptor.forClass(PriceChange.class);
        verify(priceChangeRepository).save(captor.capture());
        PriceChange change = captor.getValue();
        assertThat(change.getAccessoryId()).isEqualTo(1L);
        assertThat(change.getOldPrice()).isEqualByComparingTo("1000.00");
        assertThat(change.getNewPrice()).isEqualByComparingTo("1200.00");
        assertThat(change.getOldCostPrice()).isEqualByComparingTo("600.00");
        assertThat(change.getNewCostPrice()).isEqualByComparingTo("650.00");
        assertThat(change.getChangedBy()).isEqualTo("admin@test.local");
    }

    @Test
    void sameAmountWithDifferentScaleIsNotAPriceChange() {
        Accessory existing = accessory(1L, "1000.0", null);
        when(accessoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        service.updateAccessory(1L, new AccessoryRequest("Renamed", "Interior", new BigDecimal("1000.00"),
                "new description", null), "admin@test.local");

        verify(priceChangeRepository, never()).save(any());
    }

    private static Accessory accessory(Long id, String price, String costPrice) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setName("Mats");
        accessory.setCategory("Interior");
        accessory.setPrice(new BigDecimal(price));
        accessory.setCostPrice(costPrice == null ? null : new BigDecimal(costPrice));
        return accessory;
    }

    @Test
    void getAccessoryByIdThrowsNotFound() {
        when(accessoryRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccessoryById(4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Accessory with id 4 not found");
    }

    @Test
    void deleteAccessoryRemovesTargetedDiscountsAndMappingsBeforeDeleting() {
        when(accessoryRepository.existsById(1L)).thenReturn(true);

        service.deleteAccessory(1L);

        InOrder order = inOrder(discountRepository, accessoryRepository);
        order.verify(discountRepository).deleteByAccessoryId(1L);
        order.verify(accessoryRepository).deleteVehicleMappings(1L);
        order.verify(accessoryRepository).deleteById(1L);
    }

    @Test
    void deleteAccessoryThrowsNotFoundForUnknownId() {
        when(accessoryRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteAccessory(1L)).isInstanceOf(ResourceNotFoundException.class);
        verify(accessoryRepository, never()).deleteVehicleMappings(anyLong());
        verify(accessoryRepository, never()).deleteById(anyLong());
    }
}
