package org.example;

import jakarta.persistence.EntityManagerFactory;
import org.example.dto.QuoteRequest;
import org.example.entity.Accessory;
import org.example.entity.User;
import org.example.entity.Vehicle;
import org.example.enums.Role;
import org.example.repository.AccessoryRepository;
import org.example.repository.UserRepository;
import org.example.repository.VehicleRepository;
import org.example.service.GarageService;
import org.example.service.PricingService;
import org.example.service.VehicleService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Guards against N+1 regressions: the number of SQL statements for each hot path must not grow
// with the amount of data. If someone removes a fetch join, these tests fail.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QueryCountTest {

    private static final int VEHICLES = 3;
    private static final int ACCESSORIES_PER_VEHICLE = 4;

    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private AccessoryRepository accessoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleService vehicleService;
    @Autowired
    private GarageService garageService;
    @Autowired
    private PricingService pricingService;

    private Statistics statistics;
    private final List<Long> accessoryIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();
        User owner = userRepository.findByEmail("fleet@example.com").orElseGet(() -> {
            User user = new User();
            user.setFullName("Fleet Owner");
            user.setEmail("fleet@example.com");
            user.setPassword("not-used");
            user.setRole(Role.CUSTOMER);
            return userRepository.save(user);
        });
        for (int v = 0; v < VEHICLES; v++) {
            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleNumber("QC0" + v);
            vehicle.setMake("Make");
            vehicle.setModel("Model");
            vehicle.setYear(2022);
            vehicle.setUser(owner);
            vehicle.setPrimaryVehicle(v == 0);
            for (int a = 0; a < ACCESSORIES_PER_VEHICLE; a++) {
                Accessory accessory = new Accessory();
                accessory.setName("Part " + v + "-" + a);
                accessory.setCategory("Category " + a);
                accessory.setPrice(new BigDecimal("100.00"));
                accessoryIds.add(accessoryRepository.save(accessory).getId());
                vehicle.getAccessories().add(accessory);
            }
            vehicleRepository.save(vehicle);
        }
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Without statistics every count reads 0 and the assertions below would pass for nothing.
        assertThat(statistics.isStatisticsEnabled()).isTrue();
        statistics.clear();
    }

    @Test
    void adminVehicleListingUsesFixedQueries() {
        var page = vehicleService.getVehicles(PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(VEHICLES);
        assertThat(page.content()).allSatisfy(v -> assertThat(v.accessories()).hasSize(ACCESSORIES_PER_VEHICLE));
        // page of vehicles + count + accessories for the whole page (not one per vehicle)
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 3L);
    }

    @Test
    void garageListingUsesFixedQueries() {
        var garage = garageService.getGarage("fleet@example.com");

        assertThat(garage).hasSize(VEHICLES);
        assertThat(garage).allSatisfy(v -> assertThat(v.compatibleAccessoryCount()).isEqualTo(ACCESSORIES_PER_VEHICLE));
        // user + vehicles + one grouped count query
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 3L);
    }

    @Test
    void quoteUsesFixedQueriesWhateverTheCartSize() {
        List<QuoteRequest.Item> items = accessoryIds.stream().map(id -> new QuoteRequest.Item(id, 1)).toList();

        var quote = pricingService.quote(new QuoteRequest(items, null, null), "fleet@example.com", true);

        assertThat(quote.lines()).hasSize(VEHICLES * ACCESSORIES_PER_VEHICLE);
        // accessories + automatic discounts
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
    }
}
