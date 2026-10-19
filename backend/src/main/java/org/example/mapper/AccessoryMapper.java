package org.example.mapper;

import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.entity.Accessory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccessoryMapper {

    public Accessory toEntity(AccessoryRequest request) {
        Accessory accessory = new Accessory();
        updateEntity(accessory, request);
        return accessory;
    }

    // Copies editable fields onto an existing (managed) entity.
    public void updateEntity(Accessory accessory, AccessoryRequest request) {
        accessory.setName(request.name());
        accessory.setCategory(request.category());
        accessory.setPrice(request.price());
        accessory.setDescription(request.description());
        if (request.stockQuantity() != null) {
            accessory.setStockQuantity(request.stockQuantity());
        }
        accessory.setCostPrice(request.costPrice());
        accessory.setTaxRate(request.taxRate());
        if (request.minStockThreshold() != null) {
            accessory.setMinStockThreshold(request.minStockThreshold());
        }
        if (request.leadTimeDays() != null) {
            accessory.setLeadTimeDays(request.leadTimeDays());
        }
    }

    // Admin view, including the cost price.
    public AccessoryResponse toResponse(Accessory accessory) {
        return toResponse(accessory, true);
    }

    // Customer-facing view: the cost price is confidential (it reveals our margin).
    public AccessoryResponse toPublicResponse(Accessory accessory) {
        return toResponse(accessory, false);
    }

    private AccessoryResponse toResponse(Accessory accessory, boolean includeCost) {
        return new AccessoryResponse(
                accessory.getId(),
                accessory.getName(),
                accessory.getCategory(),
                accessory.getPrice(),
                accessory.getDescription(),
                accessory.getStockQuantity(),
                includeCost ? accessory.getCostPrice() : null,
                accessory.getTaxRate(),
                accessory.getMinStockThreshold(),
                accessory.getLeadTimeDays(),
                accessory.getCreatedAt(),
                accessory.getUpdatedAt());
    }

    public List<AccessoryResponse> toResponses(List<Accessory> accessories) {
        return accessories.stream()
                .map(this::toResponse)
                .toList();
    }
}
