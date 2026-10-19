package org.example.service;

import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.dto.PageResponse;
import org.example.dto.PriceChangeResponse;
import org.springframework.data.domain.Pageable;

// Accessory business methods.
public interface AccessoryService {

    // actingUserEmail is recorded in the price history.
    AccessoryResponse createAccessory(AccessoryRequest request, String actingUserEmail);

    PageResponse<AccessoryResponse> getAccessories(Pageable pageable);

    AccessoryResponse getAccessoryById(Long id);

    AccessoryResponse updateAccessory(Long id, AccessoryRequest request, String actingUserEmail);

    void deleteAccessory(Long id);

    // Newest first.
    PageResponse<PriceChangeResponse> getPriceHistory(Long accessoryId, Pageable pageable);
}
