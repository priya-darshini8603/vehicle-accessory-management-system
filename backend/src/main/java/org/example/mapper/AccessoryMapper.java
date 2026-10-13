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
        accessory.setName(request.name());
        accessory.setCategory(request.category());
        accessory.setPrice(request.price());
        accessory.setStockQuantity(request.stockQuantity());
        return accessory;
    }

    public AccessoryResponse toResponse(Accessory accessory) {
        return new AccessoryResponse(
                accessory.getId(),
                accessory.getName(),
                accessory.getCategory(),
                accessory.getPrice(),
                accessory.getStockQuantity());
    }

    public List<AccessoryResponse> toResponses(List<Accessory> accessories) {
        return accessories.stream()
                .map(this::toResponse)
                .toList();
    }
}
