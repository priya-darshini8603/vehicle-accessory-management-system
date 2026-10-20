package org.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.entity.Discount;
import org.example.enums.DiscountScope;
import org.example.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

// Payload for creating or replacing a discount (admin only). Leave code empty for an automatic promotion.
public record DiscountRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Pattern(regexp = "^[A-Z0-9_-]{3,32}$",
                message = "Code must be 3 to 32 letters, digits, hyphens or underscores")
        String code,

        @NotNull(message = "Type is required (PERCENTAGE or FLAT)")
        DiscountType type,

        @NotNull(message = "Value is required")
        @DecimalMin(value = "0.01", message = "Value must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Value must have at most 10 digits and 2 decimals")
        BigDecimal value,

        @NotNull(message = "Scope is required (ALL, CATEGORY or ACCESSORY)")
        DiscountScope scope,

        @Size(max = 50, message = "Category must not exceed 50 characters")
        String category,

        @Positive(message = "Accessory id must be positive")
        Long accessoryId,

        @DecimalMin(value = "0.01", message = "Maximum discount amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Maximum discount amount must have at most 10 digits and 2 decimals")
        BigDecimal maxDiscountAmount,

        @Min(value = 1, message = "Minimum quantity must be at least 1")
        @Max(value = 100, message = "Minimum quantity must not exceed 100")
        Integer minQuantity,

        Instant validFrom,

        Instant validTo,

        // Defaults to true
        Boolean active,

        // Required on PUT: the version from the last GET. A stale version means someone else saved in
        // between, and the update is rejected with 409 instead of silently overwriting their change.
        // Ignored on POST.
        Long version
) {
    // Create requests carry no version.
    public DiscountRequest(String name, String code, DiscountType type, BigDecimal value, DiscountScope scope,
                           String category, Long accessoryId, BigDecimal maxDiscountAmount, Integer minQuantity,
                           Instant validFrom, Instant validTo, Boolean active) {
        this(name, code, type, value, scope, category, accessoryId, maxDiscountAmount, minQuantity,
                validFrom, validTo, active, null);
    }

    public DiscountRequest {
        name = name == null ? null : name.trim();
        code = Discount.normalizeCode(code);
        // Stored lower-case so the quote query can compare the raw column and use idx_discounts_category.
        category = category == null || category.isBlank() ? null : category.trim().toLowerCase(Locale.ROOT);
        minQuantity = minQuantity == null ? 1 : minQuantity;
        active = active == null || active;
    }

    @JsonIgnore
    @AssertTrue(message = "A percentage discount must not exceed 100")
    public boolean isPercentageWithinRange() {
        return type != DiscountType.PERCENTAGE || value == null || value.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    @JsonIgnore
    @AssertTrue(message = "maxDiscountAmount only applies to PERCENTAGE discounts")
    public boolean isCapOnlyOnPercentage() {
        return maxDiscountAmount == null || type != DiscountType.FLAT;
    }

    // Each scope needs exactly its own target, so a discount can never silently apply to more than intended.
    @JsonIgnore
    @AssertTrue(message = "Scope ALL takes no target, CATEGORY needs category, ACCESSORY needs accessoryId")
    public boolean isTargetConsistentWithScope() {
        if (scope == null) {
            return true;
        }
        return switch (scope) {
            case ALL -> category == null && accessoryId == null;
            case CATEGORY -> category != null && accessoryId == null;
            case ACCESSORY -> accessoryId != null && category == null;
        };
    }

    @JsonIgnore
    @AssertTrue(message = "validTo must be after validFrom")
    public boolean isValidityWindowOrdered() {
        return validFrom == null || validTo == null || validTo.isAfter(validFrom);
    }
}
