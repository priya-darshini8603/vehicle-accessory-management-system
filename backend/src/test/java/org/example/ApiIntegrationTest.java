package org.example;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import org.example.entity.RefreshToken;
import org.example.repository.RefreshTokenRepository;
import org.example.scheduler.RefreshTokenCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end flow through the real security chain, JWTs, services and an H2 database.
// The bootstrap admin comes from app.admin.* in src/test/resources/application.properties.
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenCleanupJob cleanupJob;

    @Autowired
    private SecretKey jwtSigningKey;

    private record Session(String accessToken, String refreshToken) {}

    @Test
    void fullAdminAndCustomerFlow() throws Exception {
        String adminToken = login("admin@test.local", "AdminPassword123!");

        // Self-registration cannot grant ADMIN, even for a "privileged-looking" domain.
        mockMvc.perform(json(post("/api/auth/register"), """
                        {"fullName":"Asha","email":"asha@mercedes-benz.com","password":"customer-password"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        String customerToken = login("ASHA@mercedes-benz.com", "customer-password");

        // Admin creates a vehicle and two accessories.
        long vehicleId = idOf(mockMvc.perform(auth(json(post("/api/admin/vehicles"), """
                        {"vehicleNumber":" ka01ab1234 ","make":"Maruti","model":"Swift","year":2022}
                        """), adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleNumber").value("KA01AB1234"))
                .andReturn().getResponse().getContentAsString());
        long matsId = createAccessory(adminToken, "Floor Mats", "Interior");
        long rackId = createAccessory(adminToken, "Roof Rack", "Exterior");

        // Duplicate vehicle numbers are rejected regardless of case/whitespace.
        mockMvc.perform(auth(json(post("/api/admin/vehicles"), """
                        {"vehicleNumber":"KA01ab1234","make":"Tata","model":"Nexon","year":2023}
                        """), adminToken))
                .andExpect(status().isConflict());

        // Map one accessory, then bulk-replace with both.
        mockMvc.perform(auth(post("/api/admin/vehicles/{v}/accessories/{a}", vehicleId, matsId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessories", hasSize(1)));
        mockMvc.perform(auth(json(put("/api/admin/vehicles/{v}/accessories", vehicleId),
                        "{\"accessoryIds\":[" + matsId + "," + rackId + "]}"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessories", hasSize(2)));

        // Paged listing.
        mockMvc.perform(auth(get("/api/admin/vehicles?page=0&size=10&sort=vehicleNumber,asc"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accessories", hasSize(2)));

        // Customer: compatibility search works through both forms; admin API is forbidden.
        mockMvc.perform(auth(get("/api/compatibility").param("vehicleNumber", "  ka01AB1234 "), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.vehicleNumber").value("KA01AB1234"))
                .andExpect(jsonPath("$.totalAccessories").value(2))
                .andExpect(jsonPath("$.compatibleAccessories[0].name").value("Roof Rack"));
        mockMvc.perform(auth(get("/api/compatibility/vehicles/{n}/accessories", "ka01ab1234"), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccessories").value(2));
        mockMvc.perform(auth(get("/api/admin/vehicles"), customerToken))
                .andExpect(status().isForbidden());

        // Unknown vehicle number -> 404 in the standard error shape.
        mockMvc.perform(auth(get("/api/compatibility/vehicles/{n}/accessories", "zz99zz9999"), customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle with number 'ZZ99ZZ9999' not found"))
                .andExpect(jsonPath("$.path").value("/api/compatibility/vehicles/zz99zz9999/accessories"));
        mockMvc.perform(auth(get("/api/compatibility").param("vehicleNumber", "  "), customerToken))
                .andExpect(status().isBadRequest());

        // Deleting a mapped accessory detaches it; deleting the vehicle keeps its accessories.
        mockMvc.perform(auth(delete("/api/admin/accessories/{id}", matsId), adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/admin/vehicles/{id}", vehicleId), adminToken))
                .andExpect(jsonPath("$.accessories", hasSize(1)));
        mockMvc.perform(auth(delete("/api/admin/vehicles/{id}", vehicleId), adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/admin/accessories/{id}", rackId), adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/compatibility").param("vehicleNumber", "KA01AB1234"), customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderPurchaseIsIdempotentAtomicAndRestoresStockOnce() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "buyer-" + suffix + "@test.local";
        String otherEmail = "other-" + suffix + "@test.local";
        String adminToken = login("admin@test.local", "AdminPassword123!");
        mockMvc.perform(json(post("/api/auth/register"),
                        "{\"fullName\":\"Buyer\",\"email\":\"" + email
                                + "\",\"password\":\"customer-password\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(json(post("/api/auth/register"),
                        "{\"fullName\":\"Other\",\"email\":\"" + otherEmail
                                + "\",\"password\":\"customer-password\"}"))
                .andExpect(status().isCreated());
        String customerToken = login(email, "customer-password");
        String otherToken = login(otherEmail, "customer-password");

        long firstAccessory = createStockedAccessory(adminToken, "First " + suffix, "100.00", 5);
        long secondAccessory = createStockedAccessory(adminToken, "Second " + suffix, "50.00", 2);
        long shortStockAccessory = createStockedAccessory(adminToken, "Short " + suffix, "25.00", 1);
        String orderRequest = "{\"items\":[{\"accessoryId\":" + firstAccessory
                + ",\"quantity\":2},{\"accessoryId\":" + firstAccessory
                + ",\"quantity\":1},{\"accessoryId\":" + secondAccessory + ",\"quantity\":1}]}";

        String orderBody = mockMvc.perform(auth(json(post("/api/orders"), orderRequest)
                        .header("Idempotency-Key", "order-" + suffix), customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.inventoryProcessingStatus").value("DEDUCTED"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(350.00))
                .andExpect(jsonPath("$.taxAmount").value(63.00))
                .andExpect(jsonPath("$.finalTotal").value(413.00))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
                .andReturn().getResponse().getContentAsString();
        long orderId = idOf(orderBody);

        mockMvc.perform(auth(json(post("/api/orders"), orderRequest)
                        .header("Idempotency-Key", "order-" + suffix), customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId));
        mockMvc.perform(auth(json(post("/api/orders"), orderRequest.replace("\"quantity\":2", "\"quantity\":1"))
                        .header("Idempotency-Key", "order-" + suffix), customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId));

        mockMvc.perform(auth(get("/api/admin/accessories/{id}", firstAccessory), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(2));
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", firstAccessory), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("SALE"));

        String insufficientRequest = "{\"items\":[{\"accessoryId\":" + firstAccessory
                + ",\"quantity\":1},{\"accessoryId\":" + shortStockAccessory + ",\"quantity\":2}]}";
        mockMvc.perform(auth(json(post("/api/orders"), insufficientRequest)
                        .header("Idempotency-Key", "insufficient-" + suffix), customerToken))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(auth(get("/api/admin/accessories/{id}", firstAccessory), adminToken))
                .andExpect(jsonPath("$.stockQuantity").value(2));
        mockMvc.perform(auth(get("/api/admin/accessories/{id}", shortStockAccessory), adminToken))
                .andExpect(jsonPath("$.stockQuantity").value(1));
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", shortStockAccessory), adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(auth(get("/api/orders/my"), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(auth(json(put("/api/admin/accessories/{id}", firstAccessory),
                                "{\"name\":\"First " + suffix + "\",\"category\":\"Test\",\"price\":200.00}"),
                        adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(200.00));
        mockMvc.perform(auth(get("/api/orders/{id}", orderId), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.finalTotal").value(413.00));
        mockMvc.perform(auth(get("/api/orders/{id}", orderId), otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/api/orders/{id}", orderId), adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(auth(post("/api/orders/{id}/cancel", orderId), customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.inventoryProcessingStatus").value("RESTORED"));
        mockMvc.perform(auth(post("/api/orders/{id}/cancel", orderId), customerToken))
                .andExpect(status().isConflict());
        mockMvc.perform(auth(get("/api/admin/accessories/{id}", firstAccessory), adminToken))
                .andExpect(jsonPath("$.stockQuantity").value(5));
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", firstAccessory), customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", firstAccessory), adminToken))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("RESTORATION"));
    }

    @Test
    void concurrentOrdersCannotOversellTheSameAccessory() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "racer-" + suffix + "@test.local";
        String otherEmail = "racer-other-" + suffix + "@test.local";
        String adminToken = login("admin@test.local", "AdminPassword123!");
        mockMvc.perform(json(post("/api/auth/register"),
                        "{\"fullName\":\"Racer\",\"email\":\"" + email
                                + "\",\"password\":\"customer-password\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(json(post("/api/auth/register"),
                        "{\"fullName\":\"Racer Two\",\"email\":\"" + otherEmail
                                + "\",\"password\":\"customer-password\"}"))
                .andExpect(status().isCreated());
        String customerToken = login(email, "customer-password");
        String otherCustomerToken = login(otherEmail, "customer-password");
        long accessoryId = createStockedAccessory(adminToken, "Race " + suffix, "75.00", 1);

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> placeFirstOrder = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(auth(json(post("/api/orders"),
                                "{\"items\":[{\"accessoryId\":" + accessoryId + ",\"quantity\":1}]}")
                                .header("Idempotency-Key", UUID.randomUUID().toString()), customerToken))
                        .andReturn().getResponse().getStatus();
            };
            java.util.concurrent.Callable<Integer> placeSecondOrder = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(auth(json(post("/api/orders"),
                                        "{\"items\":[{\"accessoryId\":" + accessoryId + ",\"quantity\":1}]}")
                                        .header("Idempotency-Key", UUID.randomUUID().toString()),
                                otherCustomerToken))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(placeFirstOrder);
            var second = executor.submit(placeSecondOrder);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 422);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(auth(get("/api/admin/accessories/{id}", accessoryId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(0));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(json(post("/api/auth/login"), """
                        {"email":"admin@test.local","password":"definitely-wrong"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void inventoryMovementsUpdateStockAndDriveTheReport() throws Exception {
        String adminToken = login("admin@test.local", "AdminPassword123!");
        long accessoryId = idOf(mockMvc.perform(auth(json(post("/api/admin/accessories"), """
                        {"name":"Inventory Test Item","category":"Test","price":99.00,
                         "stockQuantity":4,"minStockThreshold":3,"leadTimeDays":30}
                        """), adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minStockThreshold").value(3))
                .andExpect(jsonPath("$.leadTimeDays").value(30))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(auth(json(post("/api/admin/inventory/accessories/{id}/movements", accessoryId), """
                        {"type":"SALE","quantityDelta":-3,"note":"Order 101"}
                        """), adminToken))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(auth(json(post("/api/admin/inventory/accessories/{id}/movements", accessoryId), """
                        {"type":"ADJUSTMENT","quantityDelta":-3,"note":"Physical count"}
                        """), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockBefore").value(4))
                .andExpect(jsonPath("$.stockAfter").value(1));
        mockMvc.perform(auth(json(post("/api/admin/inventory/accessories/{id}/movements", accessoryId), """
                        {"type":"RESTORATION","quantityDelta":1,"note":"Order 205"}
                        """), adminToken))
                .andExpect(status().isUnprocessableContent());

        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", accessoryId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", accessoryId)
                        .param("type", "ADJUSTMENT"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", accessoryId)
                        .param("type", "SALE"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", accessoryId)
                        .param("type", "UNKNOWN"), adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", accessoryId)
                        .param("from", "2999-01-01T00:00:00Z"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", accessoryId)
                        .param("from", "2026-10-20T00:00:00Z")
                        .param("to", "2026-10-19T00:00:00Z"), adminToken))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(auth(get("/api/admin/inventory/report").param("days", "30"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockItems[0].accessoryId").value(accessoryId))
                .andExpect(jsonPath("$.lowStockItems[0].currentStock").value(1))
                .andExpect(jsonPath("$.lowStockItems[0].unitsSold").value(0))
                .andExpect(jsonPath("$.lowStockItems[0].averageDailySales").value(0.0))
                .andExpect(jsonPath("$.lowStockItems[0].leadTimeDemand").value(0.0))
                .andExpect(jsonPath("$.lowStockItems[0].reorderPoint").value(3.0))
                .andExpect(jsonPath("$.lowStockItems[0].stockCoverDays").doesNotExist())
                .andExpect(jsonPath("$.lowStockItems[0].lowStock").value(true))
                .andExpect(jsonPath("$.lowStockItems[0].outOfStock").value(false))
                .andExpect(jsonPath("$.lowStockItems[0].recommendedReorderQuantity").value(2))
                .andExpect(jsonPath("$.recentMovements[0].type").exists());

        mockMvc.perform(auth(json(post("/api/admin/inventory/accessories/{id}/movements", accessoryId), """
                        {"type":"ADJUSTMENT","quantityDelta":1}
                        """), adminToken))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(auth(json(post("/api/admin/inventory/accessories/{id}/movements", accessoryId), """
                        {"type":"ADJUSTMENT","quantityDelta":1,"note":"Cycle count correction"}
                        """), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockAfter").value(2));
    }

    // =====================================================================================
    // Logout: authenticated by the refresh token alone, idempotent, never leaks token state
    // =====================================================================================

    @Test
    void logoutRevokesRefreshTokenButAccessTokenExpiresNaturally() throws Exception {
        Session session = loginSession("admin@test.local", "AdminPassword123!");

        logout(session.refreshToken(), session.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        mockMvc.perform(auth(get("/api/admin/accessories"), session.accessToken()))
                .andExpect(status().isOk());

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        String freshToken = login("admin@test.local", "AdminPassword123!");
        mockMvc.perform(auth(get("/api/admin/accessories"), freshToken)).andExpect(status().isOk());
    }

    @Test
    void logoutWithOnlyTheRefreshTokenRevokesTheSession() throws Exception {
        Session session = registerAndLogin();

        // No Authorization header at all.
        logout(session.refreshToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        assertThat(stored(session.refreshToken()).getRevokedAt()).isNotNull();
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithExpiredAccessTokenStillRevokesTheSession() throws Exception {
        // NOTE: this requires JwtAuthenticationFilter to let /api/auth/logout through when the
        // bearer token is expired or invalid (see the filter note in the answer).
        Session session = registerAndLogin();
        String expiredAccessToken = expiredAccessToken("admin@test.local");

        logout(session.refreshToken(), expiredAccessToken).andExpect(status().isOk());

        assertThat(stored(session.refreshToken()).getRevokedAt()).isNotNull();
    }

    @Test
    void logoutWithGarbageAccessTokenStillRevokesTheSession() throws Exception {
        Session session = registerAndLogin();

        logout(session.refreshToken(), "not.a.jwt").andExpect(status().isOk());

        assertThat(stored(session.refreshToken()).getRevokedAt()).isNotNull();
    }

    @Test
    void logoutIsIdempotent() throws Exception {
        Session session = registerAndLogin();

        logout(session.refreshToken(), null).andExpect(status().isOk());
        Instant firstRevokedAt = stored(session.refreshToken()).getRevokedAt();

        logout(session.refreshToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        // The second call must not rewrite the revocation timestamp.
        assertThat(stored(session.refreshToken()).getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    @Test
    void logoutWithUnknownTokenReturns200AndRevealsNothing() throws Exception {
        for (String unknown : List.of("random-invalid-refresh-token", "unknown-refresh-token")) {
            logout(unknown, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"));
        }
    }

    @Test
    void logoutWithExpiredRefreshTokenReturns200AndLeavesTheRowUntouched() throws Exception {
        Session session = registerAndLogin();
        RefreshToken storedToken = stored(session.refreshToken());
        storedToken.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(storedToken);

        logout(session.refreshToken(), null).andExpect(status().isOk());

        assertThat(stored(session.refreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void logoutOnlyRevokesTheGivenSessionNotTheOtherDevices() throws Exception {
        String email = registerUser();
        Session phone = loginSession(email, "customer-password");
        Session laptop = loginSession(email, "customer-password");

        logout(phone.refreshToken(), null).andExpect(status().isOk());

        assertThat(stored(phone.refreshToken()).getRevokedAt()).isNotNull();
        assertThat(stored(laptop.refreshToken()).getRevokedAt()).isNull();
        refresh(laptop.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void logoutWithAnotherUsersAccessTokenRevokesByTokenPossessionOnly() throws Exception {
        // The refresh token is the proof of identity. An unrelated valid access token neither
        // helps nor blocks the call, and no ownership error is produced.
        Session owner = registerAndLogin();
        String otherUsersAccessToken = login("admin@test.local", "AdminPassword123!");

        logout(owner.refreshToken(), otherUsersAccessToken).andExpect(status().isOk());

        assertThat(stored(owner.refreshToken()).getRevokedAt()).isNotNull();
    }

    @Test
    void logoutWithoutBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void logoutRequiresRefreshTokenField() throws Exception {
        mockMvc.perform(json(post("/api/auth/logout"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
    }

    @Test
    void logoutRejectsBlankRefreshToken() throws Exception {
        mockMvc.perform(json(post("/api/auth/logout"), "{\"refreshToken\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
    }

    @Test
    void logoutRejectsOversizedRefreshToken() throws Exception {
        String oversized = "a".repeat(129);

        mockMvc.perform(json(post("/api/auth/logout"), "{\"refreshToken\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.refreshToken").value("Refresh token is invalid"));
    }

    @Test
    void logoutRejectsMalformedJson() throws Exception {
        mockMvc.perform(json(post("/api/auth/logout"), "{\"refreshToken\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing request body"));
    }

    @Test
    void logoutIsPostOnly() throws Exception {
        mockMvc.perform(get("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    // =====================================================================================
    // Refresh: rotation, replay detection, grace window, absolute lifetime
    // =====================================================================================

    @Test
    void refreshTokenIsHashedRotatedExpiresAndRevokedOnLogout() throws Exception {
        String loginBody = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");
        RefreshToken storedToken = stored(refreshToken);
        assertThat(storedToken.getTokenHash())
                .isNotEqualTo(refreshToken)
                .hasSize(64);

        String refreshedBody = refresh(refreshToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String replacementRefreshToken = JsonPath.read(refreshedBody, "$.refreshToken");
        String replacementAccessToken = JsonPath.read(refreshedBody, "$.accessToken");
        assertThat(replacementRefreshToken).isNotEqualTo(refreshToken);
        assertThat(replacementAccessToken).isNotEqualTo(accessToken);

        // Immediate replay falls inside the grace window: rejected, nothing else is revoked.
        refresh(refreshToken).andExpect(status().isUnauthorized());

        logout(replacementRefreshToken, replacementAccessToken).andExpect(status().isOk());
        refresh(replacementRefreshToken).andExpect(status().isUnauthorized());

        String expiringLogin = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String expiringToken = JsonPath.read(expiringLogin, "$.refreshToken");
        RefreshToken expiringEntity = stored(expiringToken);
        expiringEntity.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(expiringEntity);
        refresh(expiringToken).andExpect(status().isUnauthorized());
    }

    @Test
    void rotatedTokenInheritsTheOriginalSessionExpiry() throws Exception {
        Session session = registerAndLogin();
        RefreshToken original = stored(session.refreshToken());
        // Simulate a session that is 20 days old: only 10 days remain.
        original.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        refreshTokenRepository.save(original);
        Instant expectedExpiry = stored(session.refreshToken()).getExpiresAt();

        String body = refresh(session.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String replacement = JsonPath.read(body, "$.refreshToken");

        assertThat(stored(replacement).getExpiresAt()).isEqualTo(expectedExpiry);

        // And it stays capped across a second rotation.
        String secondBody = refresh(replacement).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondReplacement = JsonPath.read(secondBody, "$.refreshToken");
        assertThat(stored(secondReplacement).getExpiresAt()).isEqualTo(expectedExpiry);
    }

    @Test
    void sessionCannotBeRefreshedPastItsAbsoluteExpiry() throws Exception {
        Session session = registerAndLogin();
        RefreshToken original = stored(session.refreshToken());
        original.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(original);

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void replayOutsideGraceWindowRevokesEveryActiveSessionOfTheUser() throws Exception {
        String email = registerUser();
        Session stolen = loginSession(email, "customer-password");
        Session otherDevice = loginSession(email, "customer-password");

        // Legitimate rotation of the first session.
        String rotatedBody = refresh(stolen.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String replacement = JsonPath.read(rotatedBody, "$.refreshToken");

        // Age the spent token so the replay is clearly not a network retry.
        RefreshToken spent = stored(stolen.refreshToken());
        spent.setRevokedAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(spent);

        refresh(stolen.refreshToken()).andExpect(status().isUnauthorized());

        // Everything the user still held is dead, including the newest token and other devices.
        assertThat(stored(replacement).getRevokedAt()).isNotNull();
        assertThat(stored(otherDevice.refreshToken()).getRevokedAt()).isNotNull();
        refresh(replacement).andExpect(status().isUnauthorized());
        refresh(otherDevice.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void replayOutsideGraceWindowDoesNotTouchOtherUsers() throws Exception {
        Session victimlessUser = registerAndLogin();
        Session attackedUser = registerAndLogin();

        String rotatedBody = refresh(attackedUser.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(rotatedBody, "$.refreshToken")).isNotBlank();
        RefreshToken spent = stored(attackedUser.refreshToken());
        spent.setRevokedAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(spent);

        refresh(attackedUser.refreshToken()).andExpect(status().isUnauthorized());

        assertThat(stored(victimlessUser.refreshToken()).getRevokedAt()).isNull();
        refresh(victimlessUser.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void replayWithinGraceWindowRejectsOnlyTheRetryAndKeepsTheNewToken() throws Exception {
        Session session = registerAndLogin();

        String rotatedBody = refresh(session.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String replacement = JsonPath.read(rotatedBody, "$.refreshToken");

        // Double submit right after the rotation.
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        assertThat(stored(replacement).getRevokedAt()).isNull();
        refresh(replacement).andExpect(status().isOk());
    }

    @Test
    void refreshWithExpiredTokenDoesNotRevokeOtherSessions() throws Exception {
        String email = registerUser();
        Session expiring = loginSession(email, "customer-password");
        Session healthy = loginSession(email, "customer-password");
        RefreshToken entity = stored(expiring.refreshToken());
        entity.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(entity);

        refresh(expiring.refreshToken()).andExpect(status().isUnauthorized());

        assertThat(stored(healthy.refreshToken()).getRevokedAt()).isNull();
        refresh(healthy.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void refreshAfterLogoutOfThatTokenIsRejected() throws Exception {
        Session session = registerAndLogin();
        logout(session.refreshToken(), null).andExpect(status().isOk());

        refresh(session.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is invalid, expired, or revoked"));
    }

    @Test
    void refreshRejectsUnknownBlankMissingAndOversizedTokens() throws Exception {
        refresh("never-issued-token").andExpect(status().isUnauthorized());
        refresh("   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
        mockMvc.perform(json(post("/api/auth/refresh"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
        refresh("a".repeat(129)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.refreshToken").value("Refresh token is invalid"));
    }

    @Test
    void concurrentRefreshWithTheSameTokenLetsExactlyOneThrough() throws Exception {
        Session session = registerAndLogin();
        String body = "{\"refreshToken\":\"" + session.refreshToken() + "\"}";

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        MockHttpServletResponse firstResponse;
        MockHttpServletResponse secondResponse;
        try {
            java.util.concurrent.Callable<MockHttpServletResponse> call = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(json(post("/api/auth/refresh"), body))
                        .andReturn().getResponse();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstResponse = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            secondResponse = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<MockHttpServletResponse> responses = List.of(firstResponse, secondResponse);
        List<MockHttpServletResponse> winners = responses.stream().filter(r -> r.getStatus() == 200).toList();
        List<MockHttpServletResponse> losers = responses.stream().filter(r -> r.getStatus() != 200).toList();

        // Single-use rotation: exactly one request wins. The loser is a plain rejection
        // (401, or 409 if the database surfaces the row-lock conflict), never a 5xx.
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(1);
        assertThat(losers.getFirst().getStatus()).isIn(401, 409);

        // The race loser was inside the grace window, so the winner's token must still work.
        String winnerRefreshToken = JsonPath.read(winners.getFirst().getContentAsString(), "$.refreshToken");
        assertThat(stored(winnerRefreshToken).getRevokedAt()).isNull();
        refresh(winnerRefreshToken).andExpect(status().isOk());
    }


    // Cleanup job

    @Test
    void cleanupJobDeletesOnlyTokensStaleForMoreThanTheRetentionPeriod() throws Exception {
        String email = registerUser();
        Session revokedLongAgo = loginSession(email, "customer-password");
        Session revokedRecently = loginSession(email, "customer-password");
        Session expiredLongAgo = loginSession(email, "customer-password");
        Session expiredRecently = loginSession(email, "customer-password");
        Session active = loginSession(email, "customer-password");

        Instant now = Instant.now();
        update(revokedLongAgo.refreshToken(), t -> t.setRevokedAt(now.minus(Duration.ofDays(8))));
        update(revokedRecently.refreshToken(), t -> t.setRevokedAt(now.minus(Duration.ofDays(6))));
        update(expiredLongAgo.refreshToken(), t -> t.setExpiresAt(now.minus(Duration.ofDays(8))));
        update(expiredRecently.refreshToken(), t -> t.setExpiresAt(now.minus(Duration.ofDays(1))));

        cleanupJob.purgeStaleTokens();

        assertThat(exists(revokedLongAgo.refreshToken())).isFalse();
        assertThat(exists(expiredLongAgo.refreshToken())).isFalse();
        assertThat(exists(revokedRecently.refreshToken())).isTrue();
        assertThat(exists(expiredRecently.refreshToken())).isTrue();
        assertThat(exists(active.refreshToken())).isTrue();

        // The surviving active session still works after the purge.
        refresh(active.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void cleanupJobIsSafeWhenThereIsNothingToDelete() {
        cleanupJob.purgeStaleTokens();
        cleanupJob.purgeStaleTokens();
    }

    // =====================================================================================
    // Other auth edge cases
    // =====================================================================================

    @Test
    void tamperedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/compatibility").param("vehicleNumber", "X")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void expiredAccessTokenIsStillRejectedOnProtectedEndpoints() throws Exception {
        // Opening up logout must not weaken any other endpoint.
        mockMvc.perform(auth(get("/api/admin/accessories"), expiredAccessToken("admin@test.local")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationValidationAndDuplicateEmail() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), """
                        {"fullName":"","email":"not-an-email","password":"short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(json(post("/api/auth/register"), """
                        {"fullName":"Admin Again","email":"Admin@Test.local","password":"another-password"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void adminCanPromoteCustomerButNotDemoteSelf() throws Exception {
        String adminToken = login("admin@test.local", "AdminPassword123!");
        long userId = idOf(mockMvc.perform(json(post("/api/auth/register"), """
                        {"fullName":"Ravi","email":"ravi@example.com","password":"customer-password"}
                        """))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(auth(json(put("/api/admin/users/{id}/role", userId), "{\"role\":\"ADMIN\"}"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        String raviToken = login("ravi@example.com", "customer-password");
        mockMvc.perform(auth(get("/api/admin/accessories"), raviToken)).andExpect(status().isOk());

        String users = mockMvc.perform(auth(get("/api/admin/users?size=100"), adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> adminIds = JsonPath.read(users, "$.content[?(@.email=='admin@test.local')].id");
        mockMvc.perform(auth(json(put("/api/admin/users/{id}/role", adminIds.getFirst().longValue()),
                        "{\"role\":\"CUSTOMER\"}"), adminToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Administrators cannot remove their own ADMIN role"));
    }

    @Test
    void loginIgnoresAStaleAuthorizationHeader() throws Exception {
        mockMvc.perform(auth(json(post("/api/auth/login"),
                                "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"),
                        expiredAccessToken("admin@test.local")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshIgnoresAStaleAuthorizationHeader() throws Exception {
        Session session = registerAndLogin();

        mockMvc.perform(auth(json(post("/api/auth/refresh"),
                        "{\"refreshToken\":\"" + session.refreshToken() + "\"}"), "not.a.jwt"))
                .andExpect(status().isOk());
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private long createAccessory(String token, String name, String category) throws Exception {
        return idOf(mockMvc.perform(auth(json(post("/api/admin/accessories"),
                        "{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"price\":999.00}"), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long createStockedAccessory(String token, String name, String price, int stock) throws Exception {
        return idOf(mockMvc.perform(auth(json(post("/api/admin/accessories"),
                        "{\"name\":\"" + name + "\",\"category\":\"Test\",\"price\":" + price
                                + ",\"stockQuantity\":" + stock + "}"), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return loginSession(email, password).accessToken();
    }

    private Session loginSession(String email, String password) throws Exception {
        String body = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    /** Registers a unique customer so token-family tests never interfere with each other. */
    private String registerUser() throws Exception {
        String email = "session-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        mockMvc.perform(json(post("/api/auth/register"),
                        "{\"fullName\":\"Session User\",\"email\":\"" + email
                                + "\",\"password\":\"customer-password\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private Session registerAndLogin() throws Exception {
        return loginSession(registerUser(), "customer-password");
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(json(post("/api/auth/refresh"),
                "{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions logout(String refreshToken, String accessToken)
            throws Exception {
        MockHttpServletRequestBuilder request = json(post("/api/auth/logout"),
                "{\"refreshToken\":\"" + refreshToken + "\"}");
        return mockMvc.perform(accessToken == null ? request : auth(request, accessToken));
    }

    private RefreshToken stored(String rawRefreshToken) throws Exception {
        return refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken)).orElseThrow();
    }

    private boolean exists(String rawRefreshToken) throws Exception {
        return refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken)).isPresent();
    }

    private void update(String rawRefreshToken, java.util.function.Consumer<RefreshToken> change)
            throws Exception {
        RefreshToken token = stored(rawRefreshToken);
        change.accept(token);
        refreshTokenRepository.save(token);
    }

    /** A correctly signed access token that expired an hour ago. */
    private String expiredAccessToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(Date.from(now.minus(Duration.ofHours(2))))
                .expiration(Date.from(now.minus(Duration.ofHours(1))))
                .signWith(jwtSigningKey)
                .compact();
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

}