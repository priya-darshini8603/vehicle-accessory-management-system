package org.example.controller;

import org.example.config.SecurityConfig;
import org.example.dto.VehicleRequest;
import org.example.dto.VehicleResponse;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.service.JwtService;
import org.example.security.RestAccessDeniedHandler;
import org.example.security.RestAuthenticationEntryPoint;
import org.example.security.RevokedTokenService;
import org.example.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehicleController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, RevokedTokenService.class})
class VehicleControllerTest {

    private static final String VALID_BODY = """
            {"vehicleNumber":" ka01ab1234 ","make":"Maruti","model":"Swift","year":2022}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleService vehicleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousRequestGets401WithStandardBody() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/admin/vehicles"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerGets403() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createReturns201WithLocationAndNormalizedNumber() throws Exception {
        when(vehicleService.createVehicle(any())).thenReturn(response(1L, "KA01AB1234"));

        mockMvc.perform(post("/api/admin/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/admin/vehicles/1"))
                .andExpect(jsonPath("$.vehicleNumber").value("KA01AB1234"));

        verify(vehicleService).createVehicle(new VehicleRequest("KA01AB1234", "Maruti", "Swift", 2022, null));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/admin/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleNumber":"","make":"Maruti","year":1500}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.vehicleNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.model").value("Model is required"))
                .andExpect(jsonPath("$.fieldErrors.year").value("Year must be 1886 or later"));

        verify(vehicleService, never()).createVehicle(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/vehicles").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing request body"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateNumberReturns409() throws Exception {
        when(vehicleService.createVehicle(any()))
                .thenThrow(new DuplicateResourceException("Vehicle with number 'KA01AB1234' already exists"));

        mockMvc.perform(post("/api/admin/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Vehicle with number 'KA01AB1234' already exists"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownVehicleReturns404() throws Exception {
        when(vehicleService.getVehicleById(9L)).thenThrow(ResourceNotFoundException.of("Vehicle", 9L));

        mockMvc.perform(get("/api/admin/vehicles/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle with id 9 not found"))
                .andExpect(jsonPath("$.path").value("/api/admin/vehicles/9"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'id'"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/vehicles/1")).andExpect(status().isNoContent());
        verify(vehicleService).deleteVehicle(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void bulkMappingRequiresIdList() throws Exception {
        mockMvc.perform(put("/api/admin/vehicles/1/accessories").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accessoryIds").exists());

        verify(vehicleService, never()).replaceAccessories(anyLong(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void bulkMappingPassesIdsToService() throws Exception {
        when(vehicleService.replaceAccessories(eq(1L), any())).thenReturn(response(1L, "KA01AB1234"));

        mockMvc.perform(put("/api/admin/vehicles/1/accessories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessoryIds\":[3,4]}"))
                .andExpect(status().isOk());

        verify(vehicleService).replaceAccessories(1L, Set.of(3L, 4L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void wrongHttpMethodReturns405InStandardShape() throws Exception {
        mockMvc.perform(post("/api/admin/vehicles/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    private static VehicleResponse response(Long id, String number) {
        Instant now = Instant.now();
        return new VehicleResponse(id, number, "Maruti", "Swift", 2022, null, List.of(), now, now);
    }
}
