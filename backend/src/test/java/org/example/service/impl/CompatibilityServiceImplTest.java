package org.example.service.impl;

import org.example.dto.CompatibilityResponse;
import org.example.entity.Accessory;
import org.example.entity.Vehicle;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.AccessoryMapper;
import org.example.mapper.VehicleMapper;
import org.example.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompatibilityServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    private CompatibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CompatibilityServiceImpl(vehicleRepository, new VehicleMapper(new AccessoryMapper()));
    }

    @Test
    void searchNormalizesInputAndReturnsAccessoriesSortedByCategoryThenName() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setVehicleNumber("KA01AB1234");
        vehicle.setMake("Maruti");
        vehicle.setModel("Swift");
        vehicle.setYear(2022);
        vehicle.addAccessory(accessory(1L, "Seat Covers", "Interior"));
        vehicle.addAccessory(accessory(2L, "Roof Rack", "Exterior"));
        vehicle.addAccessory(accessory(3L, "Floor Mats", "Interior"));
        when(vehicleRepository.findByVehicleNumberWithAccessories("KA01AB1234")).thenReturn(Optional.of(vehicle));

        CompatibilityResponse response = service.findByVehicleNumber("  ka01Ab1234 ");

        assertThat(response.vehicle().vehicleNumber()).isEqualTo("KA01AB1234");
        assertThat(response.totalAccessories()).isEqualTo(3);
        assertThat(response.compatibleAccessories()).extracting(a -> a.name())
                .containsExactly("Roof Rack", "Floor Mats", "Seat Covers");
    }

    @Test
    void searchThrowsNotFoundWithNormalizedNumberInMessage() {
        when(vehicleRepository.findByVehicleNumberWithAccessories("KA01AB1234")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByVehicleNumber(" ka01ab1234"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vehicle with number 'KA01AB1234' not found");
    }

    @Test
    void modelSearchTrimsEscapesWildcardsAndReturnsOnlyMappedAccessories() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setVehicleNumber("KA01AB1234");
        vehicle.setMake("Maruti");
        vehicle.setModel("Swift 100%_!");
        vehicle.setYear(2022);
        vehicle.addAccessory(accessory(1L, "Floor Mats", "Interior"));
        when(vehicleRepository.findByModelNamePatternWithAccessories("%100!%!_!!%"))
                .thenReturn(List.of(vehicle));

        List<CompatibilityResponse> responses = service.findByModelName(" 100%_! ");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().vehicle().model()).isEqualTo("Swift 100%_!");
        assertThat(responses.getFirst().compatibleAccessories())
                .extracting(a -> a.name())
                .containsExactly("Floor Mats");
    }

    @Test
    void modelSearchReturnsEmptyListWhenThereAreNoMatches() {
        when(vehicleRepository.findByModelNamePatternWithAccessories("%unknown%")).thenReturn(List.of());

        assertThat(service.findByModelName("unknown")).isEmpty();
    }

    private static Accessory accessory(Long id, String name, String category) {
        Accessory a = new Accessory();
        a.setId(id);
        a.setName(name);
        a.setCategory(category);
        a.setPrice(new BigDecimal("500.00"));
        return a;
    }
}
