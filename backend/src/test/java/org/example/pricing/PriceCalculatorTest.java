package org.example.pricing;

import org.example.entity.Accessory;
import org.example.entity.Discount;
import org.example.enums.DiscountScope;
import org.example.enums.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final PriceCalculator calculator = new PriceCalculator(new BigDecimal("18.00"));

    @Test
    void noDiscountChargesDefaultTaxOnBasePrice() {
        PricedLine line = calculator.price(accessory(1L, "1000.00", null), 2, List.of(), NOW);

        assertThat(line.subtotal()).isEqualByComparingTo("2000.00");
        assertThat(line.discountTotal()).isEqualByComparingTo("0.00");
        assertThat(line.taxRate()).isEqualByComparingTo("18.00");
        assertThat(line.taxAmount()).isEqualByComparingTo("360.00");
        assertThat(line.total()).isEqualByComparingTo("2360.00");
        assertThat(line.appliedDiscount()).isNull();
    }

    @Test
    void accessoryTaxRateOverridesDefaultAndIsChargedOnDiscountedPrice() {
        Accessory accessory = accessory(1L, "1000.00", null);
        accessory.setTaxRate(new BigDecimal("28.00"));

        PricedLine line = calculator.price(accessory, 1, List.of(percent(1L, "10", DiscountScope.ALL)), NOW);

        assertThat(line.discountedUnitPrice()).isEqualByComparingTo("900.00");
        // 28% of 900, not of 1000
        assertThat(line.taxAmount()).isEqualByComparingTo("252.00");
        assertThat(line.total()).isEqualByComparingTo("1152.00");
    }

    @Test
    void discountsDoNotStackTheBiggestSavingWins() {
        Discount tenPercent = percent(1L, "10", DiscountScope.ALL);
        Discount flat150 = flat(2L, "150");

        PricedLine line = calculator.price(accessory(1L, "1000.00", null), 1, List.of(tenPercent, flat150), NOW);

        assertThat(line.unitDiscount()).isEqualByComparingTo("150.00");
        assertThat(line.appliedDiscount()).isSameAs(flat150);
    }

    @Test
    void equalSavingsPickTheLowestIdSoQuotesAreRepeatable() {
        Discount later = flat(9L, "100");
        Discount earlier = flat(3L, "100");

        PricedLine line = calculator.price(accessory(1L, "1000.00", null), 1, List.of(later, earlier), NOW);

        assertThat(line.appliedDiscount()).isSameAs(earlier);
    }

    @Test
    void percentageDiscountIsCappedByMaxDiscountAmount() {
        Discount capped = percent(1L, "50", DiscountScope.ALL);
        capped.setMaxDiscountAmount(new BigDecimal("500.00"));

        PricedLine line = calculator.price(accessory(1L, "8000.00", null), 1, List.of(capped), NOW);

        assertThat(line.unitDiscount()).isEqualByComparingTo("500.00");
    }

    @Test
    void flatDiscountLargerThanPriceNeverMakesThePriceNegative() {
        PricedLine line = calculator.price(accessory(1L, "300.00", null), 1, List.of(flat(1L, "500")), NOW);

        assertThat(line.discountedUnitPrice()).isEqualByComparingTo("0.00");
        assertThat(line.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void discountIsCutBackToKeepThePriceAtCost() {
        PricedLine line = calculator.price(accessory(1L, "1000.00", "850.00"), 1,
                List.of(percent(1L, "30", DiscountScope.ALL)), NOW);

        assertThat(line.discountedUnitPrice()).isEqualByComparingTo("850.00");
        assertThat(line.unitDiscount()).isEqualByComparingTo("150.00");
        assertThat(line.marginFloorApplied()).isTrue();
    }

    @Test
    void discountOutsideItsWindowOrInactiveIsIgnored() {
        Discount expired = flat(1L, "100");
        expired.setValidTo(NOW.minus(Duration.ofMinutes(1)));
        Discount notStarted = flat(2L, "100");
        notStarted.setValidFrom(NOW.plus(Duration.ofDays(1)));
        Discount paused = flat(3L, "100");
        paused.setActive(false);
        Discount endsExactlyNow = flat(4L, "100");
        endsExactlyNow.setValidTo(NOW);

        PricedLine line = calculator.price(accessory(1L, "1000.00", null), 1,
                List.of(expired, notStarted, paused, endsExactlyNow), NOW);

        assertThat(line.appliedDiscount()).isNull();
    }

    @Test
    void bulkDiscountNeedsTheMinimumQuantity() {
        Discount buyFour = percent(1L, "15", DiscountScope.ALL);
        buyFour.setMinQuantity(4);
        Accessory tyre = accessory(1L, "5000.00", null);

        assertThat(calculator.price(tyre, 3, List.of(buyFour), NOW).appliedDiscount()).isNull();
        assertThat(calculator.price(tyre, 4, List.of(buyFour), NOW).discountTotal()).isEqualByComparingTo("3000.00");
    }

    @Test
    void scopeMatchesCategoryIgnoringCaseAndOnlyTheTargetAccessory() {
        Discount interior = percent(1L, "10", DiscountScope.CATEGORY);
        interior.setCategory(" interior ");
        Accessory mats = accessory(1L, "1000.00", null);
        Discount onlyAccessory2 = percent(2L, "20", DiscountScope.ACCESSORY);
        onlyAccessory2.setAccessory(accessory(2L, "1.00", null));

        PricedLine line = calculator.price(mats, 1, List.of(interior, onlyAccessory2), NOW);

        assertThat(line.appliedDiscount()).isSameAs(interior);
    }

    @Test
    void roundingIsPerUnitHalfUpSoTotalsReconcile() {
        // 33.33% of 99.99 = 33.3267 -> 33.33 per unit
        PricedLine line = calculator.price(accessory(1L, "99.99", null), 3,
                List.of(percent(1L, "33.33", DiscountScope.ALL)), NOW);

        assertThat(line.unitDiscount()).isEqualByComparingTo("33.33");
        assertThat(line.discountTotal()).isEqualByComparingTo("99.99");
        assertThat(line.subtotal().subtract(line.discountTotal()).add(line.taxAmount()))
                .isEqualByComparingTo(line.total());
    }

    private static Accessory accessory(Long id, String price, String costPrice) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setName("Accessory " + id);
        accessory.setCategory("Interior");
        accessory.setPrice(new BigDecimal(price));
        accessory.setCostPrice(costPrice == null ? null : new BigDecimal(costPrice));
        return accessory;
    }

    private static Discount percent(Long id, String value, DiscountScope scope) {
        return discount(id, DiscountType.PERCENTAGE, value, scope);
    }

    private static Discount flat(Long id, String value) {
        return discount(id, DiscountType.FLAT, value, DiscountScope.ALL);
    }

    private static Discount discount(Long id, DiscountType type, String value, DiscountScope scope) {
        Discount discount = new Discount();
        discount.setId(id);
        discount.setName("Discount " + id);
        discount.setType(type);
        discount.setValue(new BigDecimal(value));
        discount.setScope(scope);
        return discount;
    }
}
