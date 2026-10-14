package org.example.service;

import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.dto.PageResponse;
import org.springframework.data.domain.Pageable;

// Accessory business methods.
public interface AccessoryService {

    AccessoryResponse createAccessory(AccessoryRequest request);

    PageResponse<AccessoryResponse> getAccessories(Pageable pageable);

    AccessoryResponse getAccessoryById(Long id);

    AccessoryResponse updateAccessory(Long id, AccessoryRequest request);

    void deleteAccessory(Long id);
}
