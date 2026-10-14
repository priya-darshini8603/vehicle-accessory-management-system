package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.dto.PageResponse;
import org.example.entity.Accessory;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.AccessoryMapper;
import org.example.repository.AccessoryRepository;
import org.example.service.AccessoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessoryServiceImpl implements AccessoryService {

    // Saves and reads accessory records.
    private final AccessoryRepository accessoryRepository;
    private final AccessoryMapper accessoryMapper;

    @Override
    @Transactional
    public AccessoryResponse createAccessory(AccessoryRequest request) {
        Accessory savedAccessory = accessoryRepository.save(accessoryMapper.toEntity(request));
        log.info("Created accessory with id {}", savedAccessory.getId());
        return accessoryMapper.toResponse(savedAccessory);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccessoryResponse> getAccessories(Pageable pageable) {
        Page<Accessory> page = accessoryRepository.findAll(pageable);
        log.debug("Retrieved {} of {} accessories", page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.from(page, accessoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessoryResponse getAccessoryById(Long id) {
        Accessory accessory = findAccessoryById(id);
        log.debug("Retrieved accessory with id {}", id);
        return accessoryMapper.toResponse(accessory);
    }

    @Override
    @Transactional
    public AccessoryResponse updateAccessory(Long id, AccessoryRequest request) {
        Accessory existing = findAccessoryById(id);
        accessoryMapper.updateEntity(existing, request);
        // Flush so @UpdateTimestamp is applied before the response is built.
        accessoryRepository.flush();
        log.info("Updated accessory with id {}", id);
        return accessoryMapper.toResponse(existing);
    }

    /**
     * Deletes the accessory after removing it from every vehicle it is mapped to.
     */
    @Override
    @Transactional
    public void deleteAccessory(Long id) {
        if (!accessoryRepository.existsById(id)) {
            log.warn("Accessory with id {} was not found", id);
            throw ResourceNotFoundException.of("Accessory", id);
        }
        accessoryRepository.deleteVehicleMappings(id);
        accessoryRepository.deleteById(id);
        log.info("Deleted accessory with id {}", id);
    }

    // Finds an accessory and throws a clear error if missing.
    private Accessory findAccessoryById(Long id) {
        return accessoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Accessory with id {} was not found", id);
                    return ResourceNotFoundException.of("Accessory", id);
                });
    }
}
