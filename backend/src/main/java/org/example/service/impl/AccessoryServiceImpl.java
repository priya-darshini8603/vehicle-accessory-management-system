package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.dto.PageResponse;
import org.example.entity.Accessory;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.AccessoryMapper;
import org.example.repository.AccessoryRepository;
import org.example.service.AccessoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessoryServiceImpl implements AccessoryService {

    /**
     * Data access layer that persists accessory catalog entries.
     */
    private final AccessoryRepository accessoryRepository;
    private final AccessoryMapper accessoryMapper;

    @Override
    @Transactional
    public AccessoryResponse createAccessory(AccessoryRequest request) {
        return accessoryMapper.toResponse(accessoryRepository.save(accessoryMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccessoryResponse> getAccessories(Pageable pageable) {
        return PageResponse.from(accessoryRepository.findAll(pageable), accessoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessoryResponse getAccessoryById(Long id) {
        return accessoryMapper.toResponse(findAccessoryById(id));
    }

    @Override
    @Transactional
    public AccessoryResponse updateAccessory(Long id, AccessoryRequest request) {
        Accessory existing = findAccessoryById(id);
        accessoryMapper.updateEntity(existing, request);
        // Flush so @UpdateTimestamp is applied before the response is built.
        accessoryRepository.flush();
        return accessoryMapper.toResponse(existing);
    }

    /**
     * Deletes the accessory after removing it from every vehicle it is mapped to.
     */
    @Override
    @Transactional
    public void deleteAccessory(Long id) {
        if (!accessoryRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Accessory", id);
        }
        accessoryRepository.deleteVehicleMappings(id);
        accessoryRepository.deleteById(id);
    }

    /**
     * Centralized lookup for accessories, ensuring consistent not-found handling.
     */
    private Accessory findAccessoryById(Long id) {
        return accessoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Accessory", id));
    }
}
