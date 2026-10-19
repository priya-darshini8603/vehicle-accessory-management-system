package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.QuoteRequest;
import org.example.dto.QuoteResponse;
import org.example.entity.Accessory;
import org.example.entity.Discount;
import org.example.entity.Vehicle;
import org.example.exception.BusinessRuleException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.DiscountMapper;
import org.example.pricing.PriceCalculator;
import org.example.pricing.PricedLine;
import org.example.repository.AccessoryRepository;
import org.example.repository.DiscountRepository;
import org.example.repository.VehicleRepository;
import org.example.service.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

// Builds a quote with a fixed number of queries, however many items the cart has:
// accessories (1), automatic discounts (1), coupon (0-1), vehicle with its accessories (0-1).
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    public static final String CURRENCY = "INR";

    private final AccessoryRepository accessoryRepository;
    private final DiscountRepository discountRepository;
    private final VehicleRepository vehicleRepository;
    private final PriceCalculator priceCalculator;
    private final DiscountMapper discountMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest request, String actingUserEmail, boolean admin) {
        Instant now = clock.instant();
        Map<Long, Integer> quantities = mergeQuantities(request.items());
        Map<Long, Accessory> accessories = loadAccessories(quantities.keySet());
        Vehicle vehicle = request.vehicleId() == null ? null : loadVehicle(request.vehicleId(), actingUserEmail, admin);
        Set<Long> compatibleIds = vehicle == null ? Set.of()
                : vehicle.getAccessories().stream().map(Accessory::getId).collect(Collectors.toSet());
        Discount coupon = resolveCoupon(request.couponCode(), now);

        Set<String> categories = accessories.values().stream()
                .map(a -> a.getCategory().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<Discount> candidates = new ArrayList<>(
                discountRepository.findLiveAutomaticDiscounts(now, categories, accessories.keySet()));
        if (coupon != null) {
            candidates.add(coupon);
        }

        List<QuoteResponse.Line> lines = new ArrayList<>();
        boolean couponEligible = false;
        boolean couponUsed = false;
        for (Map.Entry<Long, Integer> item : quantities.entrySet()) {
            Accessory accessory = accessories.get(item.getKey());
            int quantity = item.getValue();
            PricedLine priced = priceCalculator.price(accessory, quantity, candidates, now);
            if (coupon != null) {
                couponEligible |= priceCalculator.applies(coupon, accessory, quantity, now);
                couponUsed |= priced.appliedDiscount() == coupon;
            }
            lines.add(toLine(accessory, priced, lineWarnings(accessory, quantity, priced, vehicle, compatibleIds, admin)));
        }

        List<String> warnings = new ArrayList<>();
        if (coupon != null && !couponEligible) {
            throw new BusinessRuleException("Coupon '" + coupon.getCode() + "' does not apply to any item in this quote");
        }
        if (coupon != null && !couponUsed) {
            warnings.add("Coupon '" + coupon.getCode() + "' was not used: the automatic discounts already give a better price");
        }

        return new QuoteResponse(
                lines,
                sum(lines, QuoteResponse.Line::subtotal),
                sum(lines, QuoteResponse.Line::discountTotal),
                sum(lines, QuoteResponse.Line::taxAmount),
                sum(lines, QuoteResponse.Line::total),
                CURRENCY,
                coupon != null ? coupon.getCode() : null,
                couponUsed,
                vehicle != null ? vehicle.getId() : null,
                warnings,
                now);
    }

    // The same accessory listed twice becomes one line, so a per-line rule (minQuantity, the quantity
    // limit) cannot be dodged or double-counted by splitting it.
    private static Map<Long, Integer> mergeQuantities(List<QuoteRequest.Item> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (QuoteRequest.Item item : items) {
            quantities.merge(item.accessoryId(), item.quantity(), Integer::sum);
        }
        quantities.forEach((id, quantity) -> {
            if (quantity > QuoteRequest.MAX_QUANTITY_PER_ACCESSORY) {
                throw new BusinessRuleException("Total quantity for accessory " + id + " must not exceed "
                        + QuoteRequest.MAX_QUANTITY_PER_ACCESSORY);
            }
        });
        return quantities;
    }

    private Map<Long, Accessory> loadAccessories(Set<Long> ids) {
        Map<Long, Accessory> found = accessoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));
        if (found.size() != ids.size()) {
            Set<Long> missing = new TreeSet<>(ids);
            missing.removeAll(found.keySet());
            throw new ResourceNotFoundException("Accessories not found with ids " + missing);
        }
        return found;
    }

    // A customer asking about someone else's vehicle gets the same 404 as for a missing one,
    // so vehicle ids cannot be probed.
    private Vehicle loadVehicle(Long vehicleId, String actingUserEmail, boolean admin) {
        return (admin
                ? vehicleRepository.findByIdWithAccessories(vehicleId)
                : vehicleRepository.findByIdAndOwnerEmailWithAccessories(vehicleId, actingUserEmail))
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle", vehicleId));
    }

    // Distinct messages for each reason a coupon fails, so the customer knows what went wrong.
    private Discount resolveCoupon(String rawCode, Instant now) {
        String code = Discount.normalizeCode(rawCode);
        if (code == null) {
            return null;
        }
        Discount coupon = discountRepository.findByCode(code)
                .orElseThrow(() -> new BusinessRuleException("Coupon '" + code + "' is not valid"));
        if (!coupon.isActive()) {
            throw new BusinessRuleException("Coupon '" + code + "' is no longer active");
        }
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new BusinessRuleException("Coupon '" + code + "' is not active yet");
        }
        if (coupon.getValidTo() != null && !now.isBefore(coupon.getValidTo())) {
            throw new BusinessRuleException("Coupon '" + code + "' has expired");
        }
        return coupon;
    }

    private static List<String> lineWarnings(Accessory accessory, int quantity, PricedLine priced,
                                             Vehicle vehicle, Set<Long> compatibleIds, boolean admin) {
        List<String> warnings = new ArrayList<>();
        Integer stock = accessory.getStockQuantity();
        if (stock != null && stock == 0) {
            warnings.add("Out of stock");
        } else if (stock != null && stock < quantity) {
            warnings.add("Only " + stock + " in stock");
        }
        if (vehicle != null && !compatibleIds.contains(accessory.getId())) {
            warnings.add("Not listed as compatible with vehicle " + vehicle.getVehicleNumber());
        }
        // Admin-only: telling a customer would reveal that the price now equals our cost.
        if (admin && priced.marginFloorApplied()) {
            warnings.add("Discount reduced to keep the price at or above cost");
        }
        return warnings;
    }

    private QuoteResponse.Line toLine(Accessory accessory, PricedLine priced, List<String> warnings) {
        return new QuoteResponse.Line(
                accessory.getId(),
                accessory.getName(),
                accessory.getCategory(),
                priced.quantity(),
                priced.unitPrice(),
                priced.unitDiscount(),
                priced.discountedUnitPrice(),
                priced.subtotal(),
                priced.discountTotal(),
                priced.taxRate(),
                priced.taxAmount(),
                priced.total(),
                discountMapper.toApplied(priced.appliedDiscount()),
                warnings);
    }

    private static BigDecimal sum(List<QuoteResponse.Line> lines, Function<QuoteResponse.Line, BigDecimal> amount) {
        return lines.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(PriceCalculator.MONEY_SCALE);
    }
}
