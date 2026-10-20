package org.example.config;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.AccessoryRequest;
import org.example.dto.DiscountRequest;
import org.example.dto.GarageVehicleRequest;
import org.example.dto.RegisterRequest;
import org.example.dto.OrderRequest;
import org.example.dto.QuoteRequest;
import org.example.dto.VehicleRequest;
import org.example.enums.DiscountScope;
import org.example.enums.DiscountType;
import org.example.repository.AccessoryRepository;
import org.example.service.AccessoryService;
import org.example.service.AuthService;
import org.example.service.DiscountService;
import org.example.service.GarageService;
import org.example.service.OrderService;
import org.example.service.VehicleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Fills an empty dev database with a realistic catalog for demos: accessories with cost, tax and stock
// levels, vehicles with compatibility mappings, promotions and coupons, past orders (which drive the
// inventory report), and a customer with two vehicles in their garage.
//
// Runs only with the dev profile AND DEMO_SEED=true, and only when there are no accessories yet, so it
// never touches a database that already has data. Everything goes through the normal services, so seeded
// data passes the same validation and audit trail (price history, orders, stock movements) as real requests.
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_CUSTOMER_EMAIL = "demo.customer@vams.local";
    private static final String ACTOR = "demo-seeder";

    private final AccessoryRepository accessoryRepository;
    private final AccessoryService accessoryService;
    private final VehicleService vehicleService;
    private final DiscountService discountService;
    private final OrderService orderService;
    private final AuthService authService;
    private final GarageService garageService;
    private final Clock clock;
    private final String customerPassword;

    public DemoDataSeeder(AccessoryRepository accessoryRepository,
                          AccessoryService accessoryService,
                          VehicleService vehicleService,
                          DiscountService discountService,
                          OrderService orderService,
                          AuthService authService,
                          GarageService garageService,
                          Clock clock,
                          @Value("${app.demo.customer-password:}") String customerPassword) {
        this.accessoryRepository = accessoryRepository;
        this.accessoryService = accessoryService;
        this.vehicleService = vehicleService;
        this.discountService = discountService;
        this.orderService = orderService;
        this.authService = authService;
        this.garageService = garageService;
        this.clock = clock;
        this.customerPassword = customerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accessoryRepository.count() > 0) {
            log.info("Demo seed skipped: the database already has accessories");
            return;
        }
        if (customerPassword == null || customerPassword.length() < 12) {
            throw new IllegalStateException(
                    "DEMO_CUSTOMER_PASSWORD must be set (12+ characters) when DEMO_SEED=true");
        }

        Map<String, Long> accessories = seedAccessories();
        authService.register(new RegisterRequest("Demo Customer", DEMO_CUSTOMER_EMAIL, customerPassword,
                "+91 98765 43210"));
        seedOrderHistory(accessories);
        seedVehicles(accessories);
        seedDiscounts(accessories);
        seedCustomerGarage();
        log.info("Demo data seeded: {} accessories, 4 orders, 4 vehicles, 4 discounts, customer {}",
                accessories.size(), DEMO_CUSTOMER_EMAIL);
    }

    // Stock levels are chosen so the inventory report has something to say: one item out of stock,
    // one below its minimum, the rest healthy.
    private Map<String, Long> seedAccessories() {
        Map<String, Long> ids = new LinkedHashMap<>();
        add(ids, "mats", "All-Weather Floor Mats", "Interior", "1499.00", "900.00", null, 40, 10, 5,
                "Rubber mats with raised edges; set of 4");
        add(ids, "seat-covers", "Leatherette Seat Covers", "Interior", "6499.00", "4200.00", null, 12, 5, 10,
                "Full set, front and rear");
        add(ids, "roof-rack", "Roof Rack", "Exterior", "8999.00", "6500.00", "28.00", 3, 5, 14,
                "Aluminium cross bars, 60 kg load");
        add(ids, "mud-flaps", "Mud Flaps", "Exterior", "599.00", "300.00", null, 0, 10, 7,
                "Set of 4");
        add(ids, "car-cover", "Car Body Cover", "Exterior", "1899.00", "1100.00", null, 8, 4, 7,
                "Water-resistant, UV protected");
        add(ids, "dashcam", "Dash Camera 1080p", "Electronics", "4999.00", "3500.00", null, 25, 5, 7,
                "Front camera with night vision and loop recording");
        add(ids, "phone-mount", "Magnetic Phone Mount", "Electronics", "799.00", "350.00", null, 100, 20, 3,
                "Dashboard and vent mount");
        add(ids, "tyre", "Tyre 185/65 R15", "Tyres", "5200.00", "4100.00", "28.00", 60, 16, 10,
                "Tubeless radial; price per tyre");
        return ids;
    }

    private void add(Map<String, Long> ids, String key, String name, String category, String price,
                     String costPrice, String taxRate, int stock, int minStock, int leadTimeDays,
                     String description) {
        AccessoryRequest request = new AccessoryRequest(name, category, new BigDecimal(price), description, stock,
                new BigDecimal(costPrice), taxRate == null ? null : new BigDecimal(taxRate), minStock, leadTimeDays,
                "Opening stock (demo)");
        ids.put(key, accessoryService.createAccessory(request, ACTOR).id());
    }

    // Sales come from real orders by the demo customer (the only way stock is sold), so the inventory report
    // shows fast and slow movers and the customer has an order history. Placed before the discounts exist,
    // so these orders are at list price.
    private void seedOrderHistory(Map<String, Long> ids) {
        order(1, ids, Map.of("phone-mount", 18, "mats", 6));
        order(2, ids, Map.of("phone-mount", 12, "tyre", 4, "dashcam", 3));
        order(3, ids, Map.of("mats", 9, "tyre", 8, "dashcam", 5));
        order(4, ids, Map.of("car-cover", 2, "seat-covers", 1));
    }

    private void order(int number, Map<String, Long> ids, Map<String, Integer> lines) {
        List<QuoteRequest.Item> items = lines.entrySet().stream()
                .map(line -> new QuoteRequest.Item(ids.get(line.getKey()), line.getValue()))
                .toList();
        orderService.createOrder(new OrderRequest(items, null), DEMO_CUSTOMER_EMAIL, "demo-seed-order-" + number);
    }

    // Two Swifts of different years show that model search finds accessories across vehicles of a model.
    private void seedVehicles(Map<String, Long> ids) {
        vehicle("KA01AB1234", "Maruti Suzuki", "Swift", 2022,
                ids, "mats", "seat-covers", "mud-flaps", "car-cover", "dashcam", "phone-mount", "tyre");
        vehicle("KA03XY9876", "Maruti Suzuki", "Swift", 2019,
                ids, "mats", "mud-flaps", "phone-mount", "tyre");
        vehicle("KA05MN4321", "Hyundai", "Creta", 2023,
                ids, "mats", "roof-rack", "car-cover", "dashcam", "phone-mount", "tyre");
        vehicle("MH12CD5678", "Tata", "Nexon", 2024,
                ids, "mats", "roof-rack", "dashcam");
    }

    private void vehicle(String number, String make, String model, int year, Map<String, Long> ids,
                         String... accessoryKeys) {
        Long vehicleId = vehicleService.createVehicle(new VehicleRequest(number, make, model, year, null)).id();
        Set<Long> accessoryIds = new HashSet<>();
        for (String key : accessoryKeys) {
            accessoryIds.add(ids.get(key));
        }
        vehicleService.replaceAccessories(vehicleId, accessoryIds);
    }

    // One automatic promotion per scope, a live coupon and an expired one (to demo the error message).
    private void seedDiscounts(Map<String, Long> ids) {
        Instant now = clock.instant();
        discountService.createDiscount(new DiscountRequest("Interior Week", null, DiscountType.PERCENTAGE,
                new BigDecimal("10"), DiscountScope.CATEGORY, "Interior", null, null, 1,
                null, now.plus(Duration.ofDays(7)), true));
        discountService.createDiscount(new DiscountRequest("Buy 4 tyres, save 15%", null, DiscountType.PERCENTAGE,
                new BigDecimal("15"), DiscountScope.ACCESSORY, null, ids.get("tyre"), null, 4,
                null, null, true));
        discountService.createDiscount(new DiscountRequest("Festive Sale", "FESTIVE25", DiscountType.PERCENTAGE,
                new BigDecimal("25"), DiscountScope.ALL, null, null, new BigDecimal("1000.00"), 1,
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)), true));
        discountService.createDiscount(new DiscountRequest("New Year Sale", "NEWYEAR10", DiscountType.FLAT,
                new BigDecimal("100"), DiscountScope.ALL, null, null, null, 1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-15T00:00:00Z"), true));
    }

    // The customer claims two catalog vehicles, which shows the multi-vehicle garage and primary vehicle.
    private void seedCustomerGarage() {
        garageService.addVehicle(DEMO_CUSTOMER_EMAIL,
                new GarageVehicleRequest("KA01AB1234", "Maruti Suzuki", "Swift", 2022, "Daily driver"));
        garageService.addVehicle(DEMO_CUSTOMER_EMAIL,
                new GarageVehicleRequest("KA05MN4321", "Hyundai", "Creta", 2023, "Family SUV"));
    }
}
