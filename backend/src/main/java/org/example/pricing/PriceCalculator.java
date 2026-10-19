package org.example.pricing;

import org.example.entity.Accessory;
import org.example.entity.Discount;
import org.example.enums.DiscountScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

// Pure pricing rules, with no database access, so every rule is unit-testable.
//
// 1. Discounts do not stack: each line gets the single discount that saves the customer the most.
// 2. A PERCENTAGE discount is capped by maxDiscountAmount; a FLAT discount is a fixed amount per unit.
// 3. The discounted unit price never drops below the accessory's cost price (or zero without one).
// 4. GST is charged on the discounted price, not on the base price.
// 5. Money is rounded HALF_UP to 2 decimals per unit, then multiplied, so line totals reconcile exactly.
@Component
public class PriceCalculator {

    public static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal defaultTaxRate;

    public PriceCalculator(@Value("${app.pricing.default-tax-rate:18.00}") BigDecimal defaultTaxRate) {
        this.defaultTaxRate = defaultTaxRate;
    }

    public PricedLine price(Accessory accessory, int quantity, Collection<Discount> candidates, Instant now) {
        BigDecimal unitPrice = money(accessory.getPrice());
        BigDecimal maxUnitDiscount = unitPrice.subtract(floorOf(accessory)).max(BigDecimal.ZERO);

        // Pick the discount with the biggest saving; the lowest id breaks ties so results are repeatable.
        Discount best = null;
        BigDecimal bestRaw = BigDecimal.ZERO;
        BigDecimal bestUnitDiscount = BigDecimal.ZERO;
        for (Discount discount : candidates.stream().sorted(Comparator.comparing(Discount::getId)).toList()) {
            if (!applies(discount, accessory, quantity, now)) {
                continue;
            }
            BigDecimal raw = rawUnitDiscount(discount, unitPrice);
            BigDecimal effective = raw.min(maxUnitDiscount);
            if (effective.compareTo(bestUnitDiscount) > 0) {
                best = discount;
                bestRaw = raw;
                bestUnitDiscount = effective;
            }
        }

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal discountedUnitPrice = unitPrice.subtract(bestUnitDiscount);
        BigDecimal subtotal = unitPrice.multiply(qty);
        BigDecimal discountTotal = bestUnitDiscount.multiply(qty);
        BigDecimal taxable = subtotal.subtract(discountTotal);
        BigDecimal taxRate = taxRateOf(accessory);
        BigDecimal taxAmount = money(taxable.multiply(taxRate).divide(HUNDRED, MONEY_SCALE + 4, RoundingMode.HALF_UP));

        return new PricedLine(
                unitPrice,
                bestUnitDiscount,
                discountedUnitPrice,
                quantity,
                subtotal,
                discountTotal,
                taxRate,
                taxAmount,
                taxable.add(taxAmount),
                best,
                best != null && bestRaw.compareTo(bestUnitDiscount) > 0);
    }

    // Whether the discount is eligible for this accessory and quantity right now (ignores its size).
    public boolean applies(Discount discount, Accessory accessory, int quantity, Instant now) {
        if (!discount.isLiveAt(now) || quantity < discount.getMinQuantity()) {
            return false;
        }
        DiscountScope scope = discount.getScope();
        return switch (scope) {
            case ALL -> true;
            case CATEGORY -> discount.getCategory() != null
                    && discount.getCategory().trim().equalsIgnoreCase(accessory.getCategory().trim());
            // getId() on a lazy proxy does not trigger a query
            case ACCESSORY -> discount.getAccessory() != null
                    && Objects.equals(discount.getAccessory().getId(), accessory.getId());
        };
    }

    public BigDecimal taxRateOf(Accessory accessory) {
        return accessory.getTaxRate() != null ? accessory.getTaxRate() : defaultTaxRate;
    }

    private static BigDecimal rawUnitDiscount(Discount discount, BigDecimal unitPrice) {
        BigDecimal amount = switch (discount.getType()) {
            case PERCENTAGE -> money(unitPrice.multiply(discount.getValue())
                    .divide(HUNDRED, MONEY_SCALE + 4, RoundingMode.HALF_UP));
            case FLAT -> money(discount.getValue());
        };
        if (discount.getMaxDiscountAmount() != null) {
            amount = amount.min(money(discount.getMaxDiscountAmount()));
        }
        return amount.max(BigDecimal.ZERO);
    }

    private static BigDecimal floorOf(Accessory accessory) {
        return accessory.getCostPrice() != null ? money(accessory.getCostPrice()).max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
