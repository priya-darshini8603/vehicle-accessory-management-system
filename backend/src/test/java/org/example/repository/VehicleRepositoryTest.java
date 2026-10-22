package org.example.repository;

import org.example.entity.Accessory;
import org.example.entity.Vehicle;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VehicleRepositoryTest {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AccessoryRepository accessoryRepository;

    @Autowired
    private TestEntityManager em;

    private Vehicle swift;
    private Accessory mats;
    private Accessory roofRack;

    @BeforeEach
    void setUp() {
        mats = em.persist(accessory("Floor Mats", "Interior", "1499.00"));
        roofRack = em.persist(accessory("Roof Rack", "Exterior", "8999.50"));

        swift = vehicle("  ka01ab1234 ", "Maruti", "Swift", 2022);
        swift.addAccessory(mats);
        swift.addAccessory(roofRack);
        swift = em.persist(swift);

        em.persist(vehicle("MH12XY9999", "Honda", "City", 2021));
        em.flush();
        em.clear();
    }

    @Test
    void vehicleNumberIsNormalizedOnPersist() {
        assertThat(vehicleRepository.findByVehicleNumber("KA01AB1234")).isPresent();
        assertThat(vehicleRepository.existsByVehicleNumber("KA01AB1234")).isTrue();
    }

    @Test
    void modelKeyIsNormalizedOnPersistAndTracksModelUpdates() {
        Vehicle vehicle = vehicleRepository.findById(swift.getId()).orElseThrow();
        assertThat(vehicle.getModelKey()).isEqualTo("swift");

        vehicle.setModel("  Grand VITARA  ");
        assertThat(vehicle.getModelKey()).isEqualTo("grand vitara");
        vehicleRepository.saveAndFlush(vehicle);
        em.clear();

        Vehicle updated = vehicleRepository.findById(swift.getId()).orElseThrow();
        assertThat(updated.getModel()).isEqualTo("  Grand VITARA  ");
        assertThat(updated.getModelKey()).isEqualTo("grand vitara");
    }

    @Test
    void auditTimestampsArePopulated() {
        Vehicle v = vehicleRepository.findById(swift.getId()).orElseThrow();
        assertThat(v.getCreatedAt()).isNotNull();
        assertThat(v.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByVehicleNumberWithAccessoriesFetchesCollectionInOneQuery() {
        String input = Vehicle.normalizeVehicleNumber("  Ka01aB1234 ");

        Optional<Vehicle> result = vehicleRepository.findByVehicleNumberWithAccessories(input);

        assertThat(result).isPresent();
        assertThat(Hibernate.isInitialized(result.get().getAccessories())).isTrue();
        assertThat(result.get().getAccessories())
                .extracting(Accessory::getName)
                .containsExactlyInAnyOrder("Floor Mats", "Roof Rack");
    }

    @Test
    void findByVehicleNumberWithAccessoriesReturnsEmptyForUnknownNumber() {
        assertThat(vehicleRepository.findByVehicleNumberWithAccessories("NOPE0000")).isEmpty();
    }

    @Test
    void findByModelKeyPatternWithAccessoriesMatchesPrefixAndLoadsMappings() {
        List<Vehicle> results = vehicleRepository.findByModelKeyPatternWithAccessories("swi%");

        assertThat(results).hasSize(1);
        assertThat(Hibernate.isInitialized(results.getFirst().getAccessories())).isTrue();
        assertThat(results.getFirst().getAccessories())
                .extracting(Accessory::getName)
                .containsExactlyInAnyOrder("Floor Mats", "Roof Rack");
    }

    @Test
    void findByModelKeyPatternDoesNotMatchOnlyAnInteriorSubstring() {
        assertThat(vehicleRepository.findByModelKeyPatternWithAccessories("ift%")).isEmpty();
    }

    @Test
    void findByIdWithAccessoriesInitializesCollection() {
        Vehicle v = vehicleRepository.findByIdWithAccessories(swift.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(v.getAccessories())).isTrue();
        assertThat(v.getAccessories()).hasSize(2);
    }

    @Test
    void plainFindByIdLeavesAccessoriesLazy() {
        Vehicle v = vehicleRepository.findById(swift.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(v.getAccessories())).isFalse();
    }

    @Test
    void findAllWithAccessoriesByIdInLoadsRequestedPage() {
        List<Long> ids = vehicleRepository.findAll().stream().map(Vehicle::getId).toList();

        List<Vehicle> vehicles = vehicleRepository.findAllWithAccessoriesByIdIn(ids);

        assertThat(vehicles).hasSize(2);
        assertThat(vehicles).allMatch(v -> Hibernate.isInitialized(v.getAccessories()));
    }

    @Test
    void existsByVehicleNumberAndIdNotIgnoresSelf() {
        assertThat(vehicleRepository.existsByVehicleNumberAndIdNot("KA01AB1234", swift.getId())).isFalse();
        assertThat(vehicleRepository.existsByVehicleNumberAndIdNot("KA01AB1234", -1L)).isTrue();
    }

    @Test
    void duplicateVehicleNumberViolatesUniqueConstraint() {
        assertThatThrownBy(() -> vehicleRepository.saveAndFlush(vehicle("ka01ab1234", "Tata", "Nexon", 2023)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(Vehicle.UK_VEHICLE_NUMBER);
    }

    @Test
    void findAllByVehicleIdReturnsMappedAccessoriesSorted() {
        List<Accessory> result = accessoryRepository.findAllByVehicleId(swift.getId(), Sort.by("name").descending());

        assertThat(result).extracting(Accessory::getName).containsExactly("Roof Rack", "Floor Mats");
    }

    @Test
    void deleteVehicleMappingsAllowsCleanAccessoryDelete() {
        assertThat(accessoryRepository.isAccessoryAssignedToVehicle(mats.getId())).isTrue();

        int removed = accessoryRepository.deleteVehicleMappings(mats.getId());
        accessoryRepository.deleteById(mats.getId());
        accessoryRepository.flush();

        assertThat(removed).isEqualTo(1);
        assertThat(accessoryRepository.existsById(mats.getId())).isFalse();
        assertThat(vehicleRepository.findByIdWithAccessories(swift.getId()).orElseThrow().getAccessories())
                .extracting(Accessory::getName)
                .containsExactly("Roof Rack");
    }

    @Test
    void deletingVehicleRemovesJoinRowsButKeepsAccessories() {
        vehicleRepository.deleteById(swift.getId());
        vehicleRepository.flush();

        assertThat(accessoryRepository.count()).isEqualTo(2);
        assertThat(accessoryRepository.isAccessoryAssignedToVehicle(mats.getId())).isFalse();
    }

    private static Vehicle vehicle(String number, String make, String model, int year) {
        Vehicle v = new Vehicle();
        v.setVehicleNumber(number);
        v.setMake(make);
        v.setModel(model);
        v.setYear(year);
        return v;
    }

    private static Accessory accessory(String name, String category, String price) {
        Accessory a = new Accessory();
        a.setName(name);
        a.setCategory(category);
        a.setPrice(new BigDecimal(price));
        return a;
    }
}
