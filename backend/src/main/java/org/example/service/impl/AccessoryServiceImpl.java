package org.example.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Accessory;
import org.example.repository.AccessoryRepository;
import org.example.service.AccessoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessoryServiceImpl implements AccessoryService {

    // Saves and reads accessory records.
    private final AccessoryRepository accessoryRepository;

    @Override
    @Transactional
    public Accessory addAccessory(Accessory accessory) {
        accessory.setId(null);
        Accessory savedAccessory = accessoryRepository.save(accessory);
        log.info("Created accessory with id {}", savedAccessory.getId());
        return savedAccessory;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Accessory> getAllAccessories() {
        List<Accessory> accessories = accessoryRepository.findAll();
        log.debug("Retrieved {} accessories", accessories.size());
        return accessories;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Accessory getAccessoryById(Long id) {
        Accessory accessory = findAccessoryById(id);
        log.debug("Retrieved accessory with id {}", id);
        return accessory;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Accessory updateAccessory(Long id, Accessory details) {
        Accessory existing = findAccessoryById(id);
        existing.setName(details.getName());
        existing.setCategory(details.getCategory());
        existing.setPrice(details.getPrice());
        existing.setStockQuantity(details.getStockQuantity());
        log.info("Updated accessory with id {}", id);
        return existing;
    }

    @Override
    @Transactional
    public void deleteAccessory(Long id) {
        Accessory accessory = findAccessoryById(id);

        if (!accessory.getVehicles().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete accessory assigned to vehicles");
        }

        accessoryRepository.delete(accessory);
        log.info("Deleted accessory with id {}", id);
    }

    // Finds an accessory and throws a clear error if missing.
    private Accessory findAccessoryById(Long id) {
        return accessoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Accessory with id {} was not found", id);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Accessory not found");
                });
    }
}
