package org.example;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.example.entity.RefreshToken;
import org.example.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

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
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(json(post("/api/auth/login"), """
                        {"email":"admin@test.local","password":"definitely-wrong"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void logoutRevokesRefreshTokenButAccessTokenExpiresNaturally() throws Exception {
        String loginBody = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");

        mockMvc.perform(auth(json(post("/api/auth/logout"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        mockMvc.perform(auth(get("/api/admin/accessories"), token))
                .andExpect(status().isOk());

        mockMvc.perform(json(post("/api/auth/refresh"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        String freshToken = login("admin@test.local", "AdminPassword123!");
        mockMvc.perform(auth(get("/api/admin/accessories"), freshToken)).andExpect(status().isOk());
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockBefore").value(4))
                .andExpect(jsonPath("$.stockAfter").value(1));
        mockMvc.perform(auth(json(post("/api/admin/inventory/accessories/{id}/movements", accessoryId), """
                        {"type":"PURCHASE","quantityDelta":1,"note":"Receipt 205"}
                        """), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockAfter").value(2));

        mockMvc.perform(auth(get("/api/admin/inventory/accessories/{id}/movements", accessoryId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(auth(get("/api/admin/inventory/report").param("days", "30"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockItems[0].accessoryId").value(accessoryId))
                .andExpect(jsonPath("$.lowStockItems[0].currentStock").value(2))
                .andExpect(jsonPath("$.lowStockItems[0].unitsSold").value(3))
                .andExpect(jsonPath("$.lowStockItems[0].recommendedReorderQuantity").value(4));
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresRefreshTokenRequest() throws Exception {
        String accessToken = login("admin@test.local", "AdminPassword123!");

        mockMvc.perform(auth(json(post("/api/auth/logout"), "{}"), accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
    }

    @Test
    void logoutRejectsBlankRefreshToken() throws Exception {
        String accessToken = login("admin@test.local", "AdminPassword123!");

        mockMvc.perform(auth(json(post("/api/auth/logout"), "{\"refreshToken\":\"   \"}"), accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
    }

    @Test
    void logoutWithoutBodyReturns400() throws Exception {
        String accessToken = login("admin@test.local", "AdminPassword123!");

        mockMvc.perform(auth(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON), accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void successfulLogoutMarksStoredTokenRevoked() throws Exception {
        String loginBody = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");

        mockMvc.perform(auth(json(post("/api/auth/logout"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        RefreshToken reloaded = refreshTokenRepository.findByTokenHash(sha256(refreshToken)).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNotNull();
    }

    @Test
    void logoutRejectsAlreadyRevokedRefreshToken() throws Exception {
        String loginBody = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");

        mockMvc.perform(auth(json(post("/api/auth/logout"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"), accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(auth(json(post("/api/auth/logout"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"), accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Refresh token already revoked"));
    }

    @Test
    void logoutRejectsExpiredRefreshToken() throws Exception {
        String loginBody = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(sha256(refreshToken)).orElseThrow();
        storedToken.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(storedToken);

        mockMvc.perform(auth(json(post("/api/auth/logout"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"), accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token expired"));

        // Reload from the database; the earlier entity instance would be stale.
        RefreshToken reloaded = refreshTokenRepository.findByTokenHash(sha256(refreshToken)).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNull();
    }

    @Test
    void logoutRejectsRandomAndNonexistentRefreshTokens() throws Exception {
        String accessToken = login("admin@test.local", "AdminPassword123!");

        for (String invalidToken : List.of("random-invalid-refresh-token", "unknown-refresh-token")) {
            mockMvc.perform(auth(json(post("/api/auth/logout"),
                            "{\"refreshToken\":\"" + invalidToken + "\"}"), accessToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Refresh token not found"));
        }
    }

    @Test
    void logoutRejectsRefreshTokenOwnedByAnotherUser() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), """
                        {"fullName":"Token Owner","email":"token-owner-check@example.com",
                         "password":"customer-password"}
                        """))
                .andExpect(status().isCreated());
        String ownerLoginBody = mockMvc.perform(json(post("/api/auth/login"), """
                        {"email":"token-owner-check@example.com","password":"customer-password"}
                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ownerRefreshToken = JsonPath.read(ownerLoginBody, "$.refreshToken");
        String otherUserAccessToken = login("admin@test.local", "AdminPassword123!");

        mockMvc.perform(auth(json(post("/api/auth/logout"),
                        "{\"refreshToken\":\"" + ownerRefreshToken + "\"}"), otherUserAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Refresh token does not belong to authenticated user"));

        // Reload from the database to prove the owner's token was not revoked.
        RefreshToken reloaded = refreshTokenRepository
                .findByTokenHash(sha256(ownerRefreshToken)).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNull();
    }

    @Test
    void refreshTokenIsHashedRotatedExpiresAndRevokedOnLogout() throws Exception {
        String loginBody = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");
        String refreshHash = sha256(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(refreshHash).orElseThrow();
        assertThat(storedToken.getTokenHash())
                .isNotEqualTo(refreshToken)
                .hasSize(64);

        String refreshedBody = mockMvc.perform(json(post("/api/auth/refresh"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String replacementRefreshToken = JsonPath.read(refreshedBody, "$.refreshToken");
        String replacementAccessToken = JsonPath.read(refreshedBody, "$.accessToken");
        assertThat(replacementRefreshToken).isNotEqualTo(refreshToken);
        assertThat(replacementAccessToken).isNotEqualTo(accessToken);

        mockMvc.perform(json(post("/api/auth/refresh"),
                        "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(auth(json(post("/api/auth/logout"),
                                "{\"refreshToken\":\"" + replacementRefreshToken + "\"}"),
                        replacementAccessToken))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/auth/refresh"),
                        "{\"refreshToken\":\"" + replacementRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        String expiringLogin = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"admin@test.local\",\"password\":\"AdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String expiringToken = JsonPath.read(expiringLogin, "$.refreshToken");
        RefreshToken expiringEntity = refreshTokenRepository.findByTokenHash(sha256(expiringToken)).orElseThrow();
        expiringEntity.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(expiringEntity);
        mockMvc.perform(json(post("/api/auth/refresh"),
                        "{\"refreshToken\":\"" + expiringToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/compatibility").param("vehicleNumber", "X")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
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

    private long createAccessory(String token, String name, String category) throws Exception {
        return idOf(mockMvc.perform(auth(json(post("/api/admin/accessories"),
                        "{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"price\":999.00}"), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(json(post("/api/auth/login"),
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
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