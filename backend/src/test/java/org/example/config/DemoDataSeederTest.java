package org.example.config;

import org.example.dto.GarageVehicleResponse;
import org.example.dto.InventoryItemReport;
import org.example.dto.InventoryReport;
import org.example.dto.QuoteRequest;
import org.example.dto.QuoteResponse;
import org.example.repository.AccessoryRepository;
import org.example.repository.DiscountRepository;
import org.example.repository.VehicleRepository;
import org.example.service.AccessoryService;
import org.example.service.AuthService;
import org.example.service.DiscountService;
import org.example.service.GarageService;
import org.example.service.InventoryService;
import org.example.service.OrderService;
import org.example.service.PricingService;
import org.example.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the seeder against H2 and checks the demo story holds together. The bean itself only exists in the
// dev profile, which would also load the local .env, so the test builds it from the real services instead.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoDataSeederTest {

    @Autowired private AccessoryRepository accessoryRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private DiscountRepository discountRepository;
    @Autowired private AccessoryService accessoryService;
    @Autowired private VehicleService vehicleService;
    @Autowired private DiscountService discountService;
    @Autowired private InventoryService inventoryService;
    @Autowired private OrderService orderService;
    @Autowired private AuthService authService;
    @Autowired private GarageService garageService;
    @Autowired private PricingService pricingService;
    @Autowired private Clock clock;

    @Test
    void seedsAConsistentDemoAndSkipsWhenDataExists() {
        DemoDataSeeder seeder = new DemoDataSeeder(accessoryRepository, accessoryService, vehicleService,
                discountService, orderService, authService, garageService, clock, "demo-customer-pass");
        vehicleRepository.deleteAll();
        accessoryRepository.deleteAll();

        seeder.run(new DefaultApplicationArguments());

        assertThat(accessoryRepository.count()).isEqualTo(8);
        assertThat(vehicleRepository.count()).isEqualTo(4);
        assertThat(discountRepository.count()).isEqualTo(4);

        // The demo customer owns two vehicles; the first one claimed is primary.
        List<GarageVehicleResponse> garage = garageService.getGarage(DemoDataSeeder.DEMO_CUSTOMER_EMAIL);
        assertThat(garage).extracting(GarageVehicleResponse::vehicleNumber)
                .containsExactly("KA01AB1234", "KA05MN4321");
        assertThat(garage.getFirst().primary()).isTrue();
        assertThat(garage).allSatisfy(v -> assertThat(v.compatibleAccessoryCount()).isPositive());

        // Sales history comes from real orders the demo customer placed.
        assertThat(orderService.getMyOrders(DemoDataSeeder.DEMO_CUSTOMER_EMAIL, PageRequest.of(0, 10))
                .totalElements()).isEqualTo(4);

        // The inventory report has something to show: an out-of-stock item, a low-stock item, sales.
        InventoryReport report = inventoryService.generateReport(30);
        assertThat(report.outOfStockItems()).extracting(InventoryItemReport::name).contains("Mud Flaps");
        assertThat(report.lowStockItems()).extracting(InventoryItemReport::name).contains("Roof Rack");
        assertThat(report.fastMovingProducts()).extracting(InventoryItemReport::name)
                .contains("Magnetic Phone Mount");

        // The live coupon prices a quote for the customer's own vehicle.
        Long matsId = accessoryRepository.findAll().stream()
                .filter(a -> a.getName().equals("All-Weather Floor Mats")).findFirst().orElseThrow().getId();
        QuoteResponse quote = pricingService.quote(new QuoteRequest(
                List.of(new QuoteRequest.Item(matsId, 1)), "FESTIVE25", garage.getFirst().id()),
                DemoDataSeeder.DEMO_CUSTOMER_EMAIL, false);
        assertThat(quote.couponApplied()).isTrue();
        assertThat(quote.lines().getFirst().warnings()).isEmpty();

        // A second run never touches existing data.
        seeder.run(new DefaultApplicationArguments());
        assertThat(accessoryRepository.count()).isEqualTo(8);
        assertThat(vehicleRepository.count()).isEqualTo(4);
    }
}
