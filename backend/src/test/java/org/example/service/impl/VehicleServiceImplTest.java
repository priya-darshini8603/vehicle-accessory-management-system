package org.example.service.impl;

import org.example.dto.PageResponse;
import org.example.dto.VehicleRequest;
import org.example.dto.VehicleResponse;
import org.example.entity.Accessory;
import org.example.entity.User;
import org.example.entity.Vehicle;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.AccessoryMapper;
import org.example.mapper.VehicleMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.UserRepository;
import org.example.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private AccessoryRepository accessoryRepository;

    @Mock
    private UserRepository userRepository;

    private VehicleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VehicleServiceImpl(vehicleRepository, accessoryRepository, userRepository,
                new VehicleMapper(new AccessoryMapper()));
    }

    @Test
    void createVehicleSavesNormalizedNumberAndOwner() {
        User owner = new User();
        owner.setId(7L);
        when(vehicleRepository.existsByVehicleNumber("KA01AB1234")).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(owner));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            v.setId(1L);
            return v;
        });

        VehicleResponse response = service.createVehicle(
                new VehicleRequest(" ka01ab1234 ", "Maruti", "Swift", 2022, 7L));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.vehicleNumber()).isEqualTo("KA01AB1234");
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.accessories()).isEmpty();
    }

    @Test
    void createVehicleRejectsDuplicateNumber() {
        when(vehicleRepository.existsByVehicleNumber("KA01AB1234")).thenReturn(true);

        assertThatThrownBy(() -> service.createVehicle(request("KA01AB1234")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("KA01AB1234");
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicleFailsForUnknownOwner() {
        when(vehicleRepository.existsByVehicleNumber("KA01AB1234")).thenReturn(false);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVehicle(
                new VehicleRequest("KA01AB1234", "Maruti", "Swift", 2022, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User with id 99 not found");
    }

    @Test
    void updateVehicleRejectsNumberTakenByAnotherVehicle() {
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle(1L, "KA01AB1234")));
        when(vehicleRepository.existsByVehicleNumberAndIdNot("MH12XY9999", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateVehicle(1L, request("MH12XY9999")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateVehicleCopiesFieldsAndClearsOwnerWhenOmitted() {
        Vehicle existing = vehicle(1L, "KA01AB1234");
        existing.setUser(new User());
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(existing));
        when(vehicleRepository.existsByVehicleNumberAndIdNot("KA01AB1234", 1L)).thenReturn(false);

        VehicleResponse response = service.updateVehicle(1L,
                new VehicleRequest("KA01AB1234", "Honda", "City", 2024, null));

        assertThat(response.make()).isEqualTo("Honda");
        assertThat(response.year()).isEqualTo(2024);
        assertThat(existing.getUser()).isNull();
        verify(vehicleRepository).flush();
    }

    @Test
    void getVehicleByIdThrowsNotFound() {
        when(vehicleRepository.findByIdWithAccessories(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVehicleById(5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vehicle with id 5 not found");
    }

    @Test
    void getVehiclesLoadsAccessoriesForPageInOneBatch() {
        PageRequest pageable = PageRequest.of(0, 2);
        when(vehicleRepository.findAll(pageable)).thenReturn(
                new PageImpl<>(List.of(vehicle(1L, "A111"), vehicle(2L, "B222")), pageable, 5));

        PageResponse<VehicleResponse> page = service.getVehicles(pageable);

        verify(vehicleRepository).findAllWithAccessoriesByIdIn(List.of(1L, 2L));
        assertThat(page.content()).extracting(VehicleResponse::vehicleNumber).containsExactly("A111", "B222");
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void deleteVehicleThrowsNotFoundForUnknownId() {
        when(vehicleRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteVehicle(3L)).isInstanceOf(ResourceNotFoundException.class);
        verify(vehicleRepository, never()).delete(any());
    }

    @Test
    void addAccessoryLinksBothSidesAndIsIdempotent() {
        Vehicle vehicle = vehicle(1L, "KA01AB1234");
        Accessory mats = accessory(10L, "Floor Mats");
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle));
        when(accessoryRepository.findById(10L)).thenReturn(Optional.of(mats));

        service.addAccessory(1L, 10L);
        VehicleResponse response = service.addAccessory(1L, 10L);

        assertThat(response.accessories()).hasSize(1);
        assertThat(mats.getVehicles()).containsExactly(vehicle);
    }

    @Test
    void addAccessoryThrowsNotFoundForUnknownAccessory() {
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle(1L, "KA01AB1234")));
        when(accessoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addAccessory(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Accessory with id 10 not found");
    }

    @Test
    void removeAccessoryUnlinksMappedAccessory() {
        Vehicle vehicle = vehicle(1L, "KA01AB1234");
        Accessory mats = accessory(10L, "Floor Mats");
        vehicle.addAccessory(mats);
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle));

        VehicleResponse response = service.removeAccessory(1L, 10L);

        assertThat(response.accessories()).isEmpty();
        assertThat(mats.getVehicles()).isEmpty();
    }

    @Test
    void removeAccessoryThrowsNotFoundWhenNotMapped() {
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle(1L, "KA01AB1234")));

        assertThatThrownBy(() -> service.removeAccessory(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not mapped");
    }

    @Test
    void replaceAccessoriesSetsExactlyTheRequestedSet() {
        Vehicle vehicle = vehicle(1L, "KA01AB1234");
        vehicle.addAccessory(accessory(10L, "Floor Mats"));
        vehicle.addAccessory(accessory(11L, "Seat Covers"));
        Accessory keep = accessory(11L, "Seat Covers");
        Accessory added = accessory(12L, "Roof Rack");
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle));
        when(accessoryRepository.findAllById(Set.of(11L, 12L))).thenReturn(List.of(keep, added));

        VehicleResponse response = service.replaceAccessories(1L, Set.of(11L, 12L));

        assertThat(response.accessories()).extracting(a -> a.id()).containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    void replaceAccessoriesWithEmptySetClearsMappings() {
        Vehicle vehicle = vehicle(1L, "KA01AB1234");
        vehicle.addAccessory(accessory(10L, "Floor Mats"));
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle));
        when(accessoryRepository.findAllById(Set.of())).thenReturn(List.of());

        assertThat(service.replaceAccessories(1L, Set.of()).accessories()).isEmpty();
    }

    @Test
    void replaceAccessoriesFailsAtomicallyWhenAnyIdIsMissing() {
        Vehicle vehicle = vehicle(1L, "KA01AB1234");
        vehicle.addAccessory(accessory(10L, "Floor Mats"));
        when(vehicleRepository.findByIdWithAccessories(1L)).thenReturn(Optional.of(vehicle));
        when(accessoryRepository.findAllById(Set.of(10L, 98L, 99L))).thenReturn(List.of(accessory(10L, "Floor Mats")));

        assertThatThrownBy(() -> service.replaceAccessories(1L, Set.of(10L, 98L, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Accessories not found with ids [98, 99]");
        assertThat(vehicle.getAccessories()).hasSize(1);
    }

    private static VehicleRequest request(String number) {
        return new VehicleRequest(number, "Maruti", "Swift", 2022, null);
    }

    private static Vehicle vehicle(Long id, String number) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setVehicleNumber(number);
        v.setMake("Maruti");
        v.setModel("Swift");
        v.setYear(2022);
        return v;
    }

    private static Accessory accessory(Long id, String name) {
        Accessory a = new Accessory();
        a.setId(id);
        a.setName(name);
        a.setCategory("Interior");
        a.setPrice(new BigDecimal("999.00"));
        return a;
    }
}
