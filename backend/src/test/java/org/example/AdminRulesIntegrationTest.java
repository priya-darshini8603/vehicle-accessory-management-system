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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Regression tests for admin-side rules found untested in the 2026-10-19 eng review (T1-T3):
// discount version checks, discount edit/delete, coupon messages, admin quotes and the cost-floor warning,
// category case handling, and primary-vehicle handover when an admin moves or deletes a vehicle.
// Each test creates its own accessories and accessory-scoped discounts, so tests cannot affect each other.
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRulesIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void staleDiscountEditIsRejectedAndTheFirstChangeIsKept() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        long accessory = createAccessory(admin, "Wiper Blades", "1000.00", null);
        String created = mockMvc.perform(auth(json(post("/api/admin/discounts"),
                        discountJson("Wiper deal", "10", accessory, null)), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = idOf(created);

        // Admin A saves with the version they read.
        mockMvc.perform(auth(json(put("/api/admin/discounts/{id}", id), discountJson("Wiper deal", "15", accessory, 0L)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(15))
                .andExpect(jsonPath("$.version").value(1));

        // Admin B read version 0 before A saved: rejected, A's change survives.
        mockMvc.perform(auth(json(put("/api/admin/discounts/{id}", id), discountJson("Wiper deal", "20", accessory, 0L)), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The resource was changed by another request. Reload and try again."));
        mockMvc.perform(auth(get("/api/admin/discounts/{id}", id), admin))
                .andExpect(jsonPath("$.value").value(15))
                .andExpect(jsonPath("$.version").value(1));

        // PUT without a version cannot silently overwrite either.
        mockMvc.perform(auth(json(put("/api/admin/discounts/{id}", id), discountJson("Wiper deal", "20", accessory, null)), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletedDiscountNoLongerAppliesToQuotes() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        long accessory = createAccessory(admin, "Air Freshener", "500.00", null);
        long discount = idOf(mockMvc.perform(auth(json(post("/api/admin/discounts"),
                        discountJson("Freshener deal", "10", accessory, null)), admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        quote(admin, null, accessory, null)
                .andExpect(jsonPath("$.lines[0].appliedDiscount.name").value("Freshener deal"));

        mockMvc.perform(auth(delete("/api/admin/discounts/{id}", discount), admin)).andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/admin/discounts/{id}", discount), admin)).andExpect(status().isNotFound());
        quote(admin, null, accessory, null)
                .andExpect(jsonPath("$.lines[0].appliedDiscount").doesNotExist())
                .andExpect(jsonPath("$.lines[0].discountTotal").value(0.00));
    }

    @Test
    void pausedAndNotYetStartedCouponsGiveTheirOwnMessages() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        long accessory = createAccessory(admin, "Steering Cover", "800.00", null);
        createCoupon(admin, "PAUSED1", accessory, "\"active\":false");
        createCoupon(admin, "LATER1", accessory, "\"validFrom\":\"2099-01-01T00:00:00Z\"");

        quote(admin, null, accessory, "paused1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Coupon 'PAUSED1' is no longer active"));
        quote(admin, null, accessory, "LATER1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Coupon 'LATER1' is not active yet"));
    }

    @Test
    void adminQuotesAnyVehicleAndOnlyAdminsSeeTheCostFloorWarning() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String owner = registerAndLogin();
        String stranger = registerAndLogin();
        // 50% off would take the price to 500, below the 900 cost, so the discount is cut back to 100.
        long accessory = createAccessory(admin, "Alloy Wheel", "1000.00", "900.00");
        mockMvc.perform(auth(json(post("/api/admin/discounts"), discountJson("Wheel clearance", "50", accessory, null)), admin))
                .andExpect(status().isCreated());
        long vehicle = addVehicle(owner, uniqueNumber());

        quote(admin, vehicle, accessory, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].discountedUnitPrice").value(900.00))
                .andExpect(jsonPath("$.lines[0].warnings", hasItem("Discount reduced to keep the price at or above cost")));
        quote(owner, vehicle, accessory, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].discountedUnitPrice").value(900.00))
                .andExpect(jsonPath("$.lines[0].warnings", not(hasItem("Discount reduced to keep the price at or above cost"))));
        quote(stranger, vehicle, accessory, null).andExpect(status().isNotFound());
    }

    @Test
    void discountCategoryIsStoredLowerCaseAndStillMatchesMixedCase() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        long accessory = createAccessory(admin, "Seat Organiser", "1200.00", null, "Cabin-T2");
        mockMvc.perform(auth(json(post("/api/admin/discounts"), """
                        {"name":"Cabin week","type":"PERCENTAGE","value":10,"scope":"CATEGORY","category":"  CABIN-t2 "}
                        """), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("cabin-t2"));

        quote(admin, null, accessory, null)
                .andExpect(jsonPath("$.lines[0].appliedDiscount.name").value("Cabin week"));
    }

    @Test
    void primaryVehicleMovesOnWhenAnAdminTransfersOrDeletesIt() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String ownerEmail = registerUser();
        String owner = login(ownerEmail, "customer-password");
        String buyerEmail = registerUser();
        String buyer = login(buyerEmail, "customer-password");
        long buyerId = userId(admin, buyerEmail);

        String first = uniqueNumber();
        long firstId = addVehicle(owner, first);
        long secondId = addVehicle(owner, uniqueNumber());
        long thirdId = addVehicle(owner, uniqueNumber());
        primaryOf(owner).andExpect(jsonPath("$[0].id").value(firstId));

        // Transfer the primary vehicle: the owner's next-oldest takes over, the buyer gets it as primary,
        // and the owner's nickname does not travel with it.
        mockMvc.perform(auth(json(put("/api/admin/vehicles/{id}", firstId), """
                        {"vehicleNumber":"%s","make":"Maruti","model":"Swift","year":2022,"userId":%d}
                        """.formatted(first, buyerId)), admin))
                .andExpect(status().isOk());
        primaryOf(owner)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[0].primary").value(true));
        primaryOf(buyer)
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[0].nickname").doesNotExist());

        // Delete the owner's new primary: the last remaining vehicle becomes primary.
        mockMvc.perform(auth(delete("/api/admin/vehicles/{id}", secondId), admin)).andExpect(status().isNoContent());
        primaryOf(owner)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(thirdId))
                .andExpect(jsonPath("$[0].primary").value(true));
    }

    // --- helpers -------------------------------------------------------------------------------

    private ResultActions quote(String token, Long vehicleId, long accessoryId, String coupon) throws Exception {
        String body = "{" + (vehicleId == null ? "" : "\"vehicleId\":" + vehicleId + ",")
                + (coupon == null ? "" : "\"couponCode\":\"" + coupon + "\",")
                + "\"items\":[{\"accessoryId\":" + accessoryId + ",\"quantity\":1}]}";
        return mockMvc.perform(auth(json(post("/api/pricing/quote"), body), token));
    }

    private ResultActions primaryOf(String token) throws Exception {
        return mockMvc.perform(auth(get("/api/me/vehicles"), token)).andExpect(status().isOk());
    }

    private static String discountJson(String name, String percent, long accessoryId, Long version) {
        return "{\"name\":\"" + name + "\",\"type\":\"PERCENTAGE\",\"value\":" + percent
                + ",\"scope\":\"ACCESSORY\",\"accessoryId\":" + accessoryId
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private void createCoupon(String admin, String code, long accessoryId, String extra) throws Exception {
        mockMvc.perform(auth(json(post("/api/admin/discounts"),
                        "{\"name\":\"" + code + "\",\"code\":\"" + code + "\",\"type\":\"FLAT\",\"value\":10,"
                                + "\"scope\":\"ACCESSORY\",\"accessoryId\":" + accessoryId + "," + extra + "}"), admin))
                .andExpect(status().isCreated());
    }

    private long createAccessory(String admin, String name, String price, String costPrice) throws Exception {
        return createAccessory(admin, name, price, costPrice, "Admin-Rules");
    }

    private long createAccessory(String admin, String name, String price, String costPrice, String category)
            throws Exception {
        String body = "{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"price\":" + price
                + ",\"stockQuantity\":10" + (costPrice == null ? "" : ",\"costPrice\":" + costPrice) + "}";
        return idOf(mockMvc.perform(auth(json(post("/api/admin/accessories"), body), admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long addVehicle(String token, String number) throws Exception {
        return idOf(mockMvc.perform(auth(json(post("/api/me/vehicles"), """
                        {"vehicleNumber":"%s","make":"Maruti","model":"Swift","year":2022,"nickname":"N-%s"}
                        """.formatted(number, number)), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long userId(String admin, String email) throws Exception {
        String users = mockMvc.perform(auth(get("/api/admin/users?size=100"), admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(users, "$.content[?(@.email=='" + email + "')].id");
        return ids.getFirst().longValue();
    }

    private static String uniqueNumber() {
        return "AR" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String registerAndLogin() throws Exception {
        return login(registerUser(), "customer-password");
    }

    private String registerUser() throws Exception {
        String email = "rules-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        mockMvc.perform(json(post("/api/auth/register"),
                        "{\"fullName\":\"Rules User\",\"email\":\"" + email
                                + "\",\"password\":\"customer-password\",\"phoneNumber\":\"+91 98765 43210\"}"))
                .andExpect(status().isCreated());
        return email;
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
