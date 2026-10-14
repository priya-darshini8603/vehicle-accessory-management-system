package org.example.service.impl;

import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.entity.Accessory;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.AccessoryMapper;
import org.example.repository.AccessoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessoryServiceImplTest {

    @Mock
    private AccessoryRepository accessoryRepository;

    private AccessoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccessoryServiceImpl(accessoryRepository, new AccessoryMapper());
    }

    @Test
    void updateAccessoryCopiesAllEditableFieldsIncludingDescription() {
        Accessory existing = new Accessory();
        existing.setId(1L);
        existing.setName("Old");
        existing.setCategory("Old");
        existing.setPrice(BigDecimal.ONE);
        existing.setDescription("old description");
        when(accessoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        AccessoryResponse response = service.updateAccessory(1L, new AccessoryRequest(
                "Floor Mats", "Interior", new BigDecimal("1499.00"), "All-weather rubber mats", 25));

        assertThat(response.name()).isEqualTo("Floor Mats");
        assertThat(response.category()).isEqualTo("Interior");
        assertThat(response.price()).isEqualByComparingTo("1499.00");
        assertThat(response.description()).isEqualTo("All-weather rubber mats");
        assertThat(response.stockQuantity()).isEqualTo(25);
        verify(accessoryRepository).flush();
    }

    @Test
    void getAccessoryByIdThrowsNotFound() {
        when(accessoryRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccessoryById(4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Accessory with id 4 not found");
    }

    @Test
    void deleteAccessoryRemovesMappingsBeforeDeleting() {
        when(accessoryRepository.existsById(1L)).thenReturn(true);

        service.deleteAccessory(1L);

        InOrder order = inOrder(accessoryRepository);
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
