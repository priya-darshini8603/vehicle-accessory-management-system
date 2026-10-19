package org.example.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.example.enums.StockMovementType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    // --- VehicleRequest ---------------------------------------------------------------

    @Test
    void validVehicleRequestIsNormalizedAndPasses() {
        VehicleRequest request = new VehicleRequest("  ka01ab1234 ", " Maruti ", " Swift ", 2022, null);

        assertThat(request.vehicleNumber()).isEqualTo("KA01AB1234");
        assertThat(request.make()).isEqualTo("Maruti");
        assertThat(request.model()).isEqualTo("Swift");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void vehicleRequestRejectsMissingAndMalformedFields() {
        VehicleRequest request = new VehicleRequest("KA 01@", "  ", null, 1700, -5L);

        assertThat(violatedFields(validator.validate(request)))
                .containsExactlyInAnyOrder("vehicleNumber", "make", "model", "year", "userId");
    }

    @Test
    void blankVehicleNumberIsRejected() {
        VehicleRequest request = new VehicleRequest("   ", "Honda", "City", 2021, null);

        assertThat(violatedFields(validator.validate(request))).contains("vehicleNumber");
    }

    // --- AccessoryRequest -------------------------------------------------------------

    @Test
    void validAccessoryRequestPassesWithOptionalFieldsOmitted() {
        AccessoryRequest request = new AccessoryRequest(" Floor Mats ", "Interior", new BigDecimal("1499.00"), null, null);

        assertThat(request.name()).isEqualTo("Floor Mats");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void accessoryRequestRejectsNegativeOrOverPrecisePriceAndBlankName() {
        AccessoryRequest negative = new AccessoryRequest("", "Interior", new BigDecimal("-1"), null, -2);
        AccessoryRequest tooPrecise = new AccessoryRequest("Mats", "Interior", new BigDecimal("10.999"), null, null);

        assertThat(violatedFields(validator.validate(negative)))
                .containsExactlyInAnyOrder("name", "price", "stockQuantity");
        assertThat(violatedFields(validator.validate(tooPrecise))).containsExactly("price");
    }

    // --- AccessoryMappingRequest ------------------------------------------------------

    @Test
    void mappingRequestAllowsEmptySetToClearMappings() {
        assertThat(validator.validate(new AccessoryMappingRequest(Set.of()))).isEmpty();
    }

    @Test
    void mappingRequestRejectsNullListAndInvalidIds() {
        Set<Long> ids = new HashSet<>();
        ids.add(null);
        ids.add(0L);

        assertThat(violatedFields(validator.validate(new AccessoryMappingRequest(null))))
                .containsExactly("accessoryIds");
        assertThat(validator.validate(new AccessoryMappingRequest(ids))).hasSize(2);
    }

    @Test
    void stockMovementTypesAndDeltasAreRestrictedToSupportedDirections() {
        assertThat(StockMovementType.values()).containsExactly(
                StockMovementType.SALE, StockMovementType.RESTORATION, StockMovementType.ADJUSTMENT);

        assertThat(validator.validate(new StockMovementRequest(StockMovementType.SALE, -1, null))).isEmpty();
        assertThat(validator.validate(new StockMovementRequest(StockMovementType.RESTORATION, 1, null))).isEmpty();
        assertThat(validator.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, -1, null))).isEmpty();
        assertThat(validator.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 1, null))).isEmpty();

        assertThat(validator.validate(new StockMovementRequest(StockMovementType.SALE, 1, null))).hasSize(1);
        assertThat(validator.validate(new StockMovementRequest(StockMovementType.RESTORATION, -1, null))).hasSize(1);
        assertThat(validator.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 0, null))).hasSize(1);
    }

    private static Set<String> violatedFields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().iterator().next().getName())
                .collect(Collectors.toSet());
    }
}
