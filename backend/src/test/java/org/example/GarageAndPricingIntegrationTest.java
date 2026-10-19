package org.example;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Customer garage (multiple vehicles per customer) and pricing, end to end.
// app.garage.max-vehicles-per-customer is 3 in the test properties.
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GarageAndPricingIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerManagesSeveralVehicles() throws Exception {
        String meera = registerAndLogin("meera@example.com");
        String arjun = registerAndLogin("arjun@example.com");

        long swift = addVehicle(meera, "KA01GA0001", "Maruti", "Swift", 2022, "Daily driver");
        long activa = addVehicle(meera, "KA01GA0002", "Honda", "Activa", 2021, "Scooter");

        // The first vehicle becomes primary automatically; primary is listed first.
        mockMvc.perform(auth(get("/api/me/vehicles"), meera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(swift))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[1].primary").value(false));

        // Switch primary; the old primary is no longer primary.
        mockMvc.perform(auth(put("/api/me/vehicles/{id}/primary", activa), meera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true));
        mockMvc.perform(auth(get("/api/me/vehicles"), meera))
                .andExpect(jsonPath("$[0].id").value(activa))
                .andExpect(jsonPath("$[?(@.primary == true)]", hasSize(1)));

        // Nicknames must be unique within one garage (ignoring case).
        mockMvc.perform(auth(json(patch("/api/me/vehicles/{id}", swift), "{\"nickname\":\"scooter\"}"), meera))
                .andExpect(status().isConflict());

        // Another customer cannot see, change or price against Meera's vehicle: plain 404, nothing leaked.
        mockMvc.perform(auth(get("/api/me/vehicles/{id}", swift), arjun)).andExpect(status().isNotFound());
        mockMvc.perform(auth(delete("/api/me/vehicles/{id}", swift), arjun)).andExpect(status().isNotFound());
        mockMvc.perform(auth(json(post("/api/pricing/quote"),
                        "{\"vehicleId\":" + swift + ",\"items\":[{\"accessoryId\":1,\"quantity\":1}]}"), arjun))
                .andExpect(status().isNotFound());

        // A number already in someone's garage cannot be added by anyone else.
        mockMvc.perform(auth(json(post("/api/me/vehicles"), vehicleJson("ka01ga0001", "Maruti", "Swift", 2022, null)), arjun))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Vehicle 'KA01GA0001' is registered to another account")));
        mockMvc.perform(auth(json(post("/api/me/vehicles"), vehicleJson("KA01GA0001", "Maruti", "Swift", 2022, null)), meera))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vehicle 'KA01GA0001' is already in your garage"));

        // Garage size limit (3 in tests).
        addVehicle(meera, "KA01GA0003", "Tata", "Nexon", 2023, null);
        mockMvc.perform(auth(json(post("/api/me/vehicles"), vehicleJson("KA01GA0004", "Kia", "Seltos", 2024, null)), meera))
                .andExpect(status().isUnprocessableContent());

        // A model year two years ahead is rejected.
        mockMvc.perform(auth(json(post("/api/me/vehicles"), vehicleJson("KA01GA0009", "Kia", "Seltos", 2100, null)), arjun))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.yearPlausible").exists());

        // Removing the primary vehicle hands primary to the oldest remaining one.
        mockMvc.perform(auth(delete("/api/me/vehicles/{id}", activa), meera)).andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/me/vehicles"), meera))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(swift))
                .andExpect(jsonPath("$[0].primary").value(true));

        // The released vehicle stays in the catalog; its next owner can add it if the details match.
        mockMvc.perform(auth(json(post("/api/me/vehicles"), vehicleJson("KA01GA0002", "Honda", "City", 2021, null)), arjun))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(auth(json(post("/api/me/vehicles"), vehicleJson("KA01GA0002", "honda", "activa", 2021, null)), arjun))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(activa))
                .andExpect(jsonPath("$.nickname").doesNotExist())
                .andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    void quoteAppliesBestDiscountTaxAndCouponRules() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String customer = registerAndLogin("priya@example.com");

        long mats = createAccessory(admin, """
                {"name":"Floor Mats","category":"Interior","price":1000.00,"costPrice":700.00,"stockQuantity":1}
                """);
        long rack = createAccessory(admin, """
                {"name":"Roof Rack","category":"Exterior","price":5000.00,"taxRate":28.00}
                """);

        // Automatic 10% on Interior, and a coupon for 25% off everything.
        createDiscount(admin, """
                {"name":"Interior week","type":"PERCENTAGE","value":10,"scope":"CATEGORY","category":"interior"}
                """);
        createDiscount(admin, """
                {"name":"Festive","code":"festive25","type":"PERCENTAGE","value":25,"scope":"ALL"}
                """);

        // Customer garage vehicle with only the mats mapped as compatible.
        long vehicle = addVehicle(customer, "KA05PR0001", "Hyundai", "Creta", 2023, null);
        mockMvc.perform(auth(post("/api/admin/vehicles/{v}/accessories/{a}", vehicle, mats), admin))
                .andExpect(status().isOk());

        // Without a coupon: mats get 10%, rack gets nothing. Tax is charged after the discount.
        mockMvc.perform(auth(json(post("/api/pricing/quote"), """
                        {"items":[{"accessoryId":%d,"quantity":1},{"accessoryId":%d,"quantity":1}]}
                        """.formatted(mats, rack)), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].discountedUnitPrice").value(900.00))
                .andExpect(jsonPath("$.lines[0].taxAmount").value(162.00))
                .andExpect(jsonPath("$.lines[1].discountTotal").value(0.00))
                .andExpect(jsonPath("$.lines[1].taxAmount").value(1400.00))
                .andExpect(jsonPath("$.grandTotal").value(7462.00))
                .andExpect(jsonPath("$.currency").value("INR"));

        // Coupon beats the 10% (no stacking); mats are floored at cost (700 -> 25% would be 750, still above).
        // Same accessory listed twice is merged into one line; stock shortfall and compatibility are flagged.
        mockMvc.perform(auth(json(post("/api/pricing/quote"), """
                        {"couponCode":" Festive25 ","vehicleId":%d,
                         "items":[{"accessoryId":%d,"quantity":1},{"accessoryId":%d,"quantity":1},{"accessoryId":%d,"quantity":1}]}
                        """.formatted(vehicle, mats, mats, rack)), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponApplied").value(true))
                .andExpect(jsonPath("$.couponCode").value("FESTIVE25"))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].appliedDiscount.code").value("FESTIVE25"))
                .andExpect(jsonPath("$.lines[0].discountedUnitPrice").value(750.00))
                .andExpect(jsonPath("$.lines[0].warnings", contains("Only 1 in stock")))
                .andExpect(jsonPath("$.lines[1].warnings", hasItem("Not listed as compatible with vehicle KA05PR0001")));

        // Coupon errors are specific.
        quoteWithCoupon(customer, mats, "NOPE").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Coupon 'NOPE' is not valid"));
        createDiscount(admin, """
                {"name":"Old sale","code":"OLD10","type":"FLAT","value":10,"scope":"ALL",
                 "validFrom":"2020-01-01T00:00:00Z","validTo":"2020-02-01T00:00:00Z"}
                """);
        quoteWithCoupon(customer, mats, "old10").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Coupon 'OLD10' has expired"));
        createDiscount(admin, """
                {"name":"Racks only","code":"RACK5","type":"FLAT","value":5,"scope":"ACCESSORY","accessoryId":%d}
                """.formatted(rack));
        quoteWithCoupon(customer, mats, "RACK5").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Coupon 'RACK5' does not apply to any item in this quote"));

        // A coupon worse than the automatic discount is accepted but reported as unused.
        createDiscount(admin, """
                {"name":"Tiny","code":"TINY1","type":"FLAT","value":1,"scope":"ALL"}
                """);
        quoteWithCoupon(customer, mats, "TINY1").andExpect(status().isOk())
                .andExpect(jsonPath("$.couponApplied").value(false))
                .andExpect(jsonPath("$.warnings", hasSize(1)));

        // Customers never see our cost price; admins do.
        mockMvc.perform(auth(get("/api/me/vehicles/{id}/accessories", vehicle), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compatibleAccessories[0].price").value(1000.00))
                .andExpect(jsonPath("$.compatibleAccessories[0].costPrice").doesNotExist());
        mockMvc.perform(auth(get("/api/admin/accessories/{id}", mats), admin))
                .andExpect(jsonPath("$.costPrice").value(700.00));
    }

    @Test
    void discountAndPriceValidationAndHistory() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        // Selling price below cost is rejected at entry.
        mockMvc.perform(auth(json(post("/api/admin/accessories"), """
                        {"name":"Horn","category":"Electrical","price":100.00,"costPrice":150.00}
                        """), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priceNotBelowCost").exists());

        // Inconsistent discounts are rejected before they can apply to the wrong products.
        mockMvc.perform(auth(json(post("/api/admin/discounts"), """
                        {"name":"Bad","type":"PERCENTAGE","value":120,"scope":"CATEGORY",
                         "validFrom":"2026-02-01T00:00:00Z","validTo":"2026-01-01T00:00:00Z"}
                        """), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.percentageWithinRange").exists())
                .andExpect(jsonPath("$.fieldErrors.targetConsistentWithScope").exists())
                .andExpect(jsonPath("$.fieldErrors.validityWindowOrdered").exists());

        createDiscount(admin, """
                {"name":"Dup","code":"DUPE1","type":"FLAT","value":10,"scope":"ALL"}
                """);
        mockMvc.perform(auth(json(post("/api/admin/discounts"), """
                        {"name":"Dup again","code":"dupe1","type":"FLAT","value":20,"scope":"ALL"}
                        """), admin))
                .andExpect(status().isConflict());

        // Every price change is recorded with who made it; history survives deleting the accessory.
        long horn = createAccessory(admin, """
                {"name":"Horn","category":"Electrical","price":100.00}
                """);
        mockMvc.perform(auth(json(put("/api/admin/accessories/{id}", horn), """
                        {"name":"Horn","category":"Electrical","price":120.00}
                        """), admin))
                .andExpect(status().isOk());
        createDiscount(admin, """
                {"name":"Horn deal","type":"FLAT","value":5,"scope":"ACCESSORY","accessoryId":%d}
                """.formatted(horn));
        mockMvc.perform(auth(delete("/api/admin/accessories/{id}", horn), admin)).andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/admin/accessories/{id}/price-history", horn), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].oldPrice").value(100.00))
                .andExpect(jsonPath("$.content[0].newPrice").value(120.00))
                .andExpect(jsonPath("$.content[0].changedBy").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.content[1].oldPrice").doesNotExist());
        mockMvc.perform(auth(get("/api/admin/accessories/999999/price-history"), admin))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions quoteWithCoupon(
            String token, long accessoryId, String code) throws Exception {
        return mockMvc.perform(auth(json(post("/api/pricing/quote"), """
                {"couponCode":"%s","items":[{"accessoryId":%d,"quantity":1}]}
                """.formatted(code, accessoryId)), token));
    }

    private long addVehicle(String token, String number, String make, String model, int year, String nickname)
            throws Exception {
        return idOf(mockMvc.perform(auth(json(post("/api/me/vehicles"),
                        vehicleJson(number, make, model, year, nickname)), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static String vehicleJson(String number, String make, String model, int year, String nickname) {
        return """
                {"vehicleNumber":"%s","make":"%s","model":"%s","year":%d%s}
                """.formatted(number, make, model, year,
                nickname == null ? "" : ",\"nickname\":\"" + nickname + "\"");
    }

    private long createAccessory(String token, String body) throws Exception {
        return idOf(mockMvc.perform(auth(json(post("/api/admin/accessories"), body), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long createDiscount(String token, String body) throws Exception {
        return idOf(mockMvc.perform(auth(json(post("/api/admin/discounts"), body), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"customer-password\"}"))
                .andExpect(status().isCreated());
        return login(email, "customer-password");
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
