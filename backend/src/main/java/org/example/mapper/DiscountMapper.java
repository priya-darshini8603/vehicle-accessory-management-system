package org.example.mapper;

import org.example.dto.DiscountRequest;
import org.example.dto.DiscountResponse;
import org.example.dto.QuoteResponse;
import org.example.entity.Discount;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DiscountMapper {

    // Copies editable fields; the target accessory is resolved by the service.
    public void updateEntity(Discount discount, DiscountRequest request) {
        discount.setName(request.name());
        discount.setCode(request.code());
        discount.setType(request.type());
        discount.setValue(request.value());
        discount.setScope(request.scope());
        discount.setCategory(request.category());
        discount.setMaxDiscountAmount(request.maxDiscountAmount());
        discount.setMinQuantity(request.minQuantity());
        discount.setValidFrom(request.validFrom());
        discount.setValidTo(request.validTo());
        discount.setActive(request.active());
    }

    public DiscountResponse toResponse(Discount discount) {
        return new DiscountResponse(
                discount.getId(),
                discount.getName(),
                discount.getCode(),
                discount.getType(),
                discount.getValue(),
                discount.getScope(),
                discount.getCategory(),
                // getId() on a lazy proxy does not trigger initialization
                discount.getAccessory() != null ? discount.getAccessory().getId() : null,
                discount.getMaxDiscountAmount(),
                discount.getMinQuantity(),
                discount.getValidFrom(),
                discount.getValidTo(),
                discount.isActive(),
                discount.isLiveAt(Instant.now()),
                discount.getCreatedAt(),
                discount.getUpdatedAt());
    }

    public QuoteResponse.AppliedDiscount toApplied(Discount discount) {
        return discount == null ? null : new QuoteResponse.AppliedDiscount(
                discount.getId(), discount.getName(), discount.getCode(), discount.getType(), discount.getValue());
    }
}
