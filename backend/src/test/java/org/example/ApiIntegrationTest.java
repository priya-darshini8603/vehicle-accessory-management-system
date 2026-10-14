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

import java.util.List;

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
    void logoutRevokesTokenButNotNewLogins() throws Exception {
        String token = login("admin@test.local", "AdminPassword123!");

        mockMvc.perform(auth(post("/api/auth/logout"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        mockMvc.perform(auth(get("/api/admin/accessories"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Token has been revoked. Please log in again."));

        String freshToken = login("admin@test.local", "AdminPassword123!");
        mockMvc.perform(auth(get("/api/admin/accessories"), freshToken)).andExpect(status().isOk());
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
