package org.example.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class AccessoryServiceImpl implements AccessoryService {

    /**
     * Data access layer that persists accessory catalog entries.
     */
    private final AccessoryRepository accessoryRepository;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Accessory addAccessory(Accessory accessory) {
        accessory.setId(null);
        return accessoryRepository.save(accessory);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<Accessory> getAllAccessories() {
        return accessoryRepository.findAll();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Accessory getAccessoryById(Long id) {
        return findAccessoryById(id);
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
        return existing;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteAccessory(Long id) {
        Accessory accessory = findAccessoryById(id);

        if (!accessory.getVehicles().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete accessory assigned to vehicles");
        }

        accessoryRepository.delete(accessory);
    }

    /**
     * Centralized lookup for accessories, ensuring consistent not-found handling.
     */
    private Accessory findAccessoryById(Long id) {
        return accessoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Accessory not found"));
    }
}
