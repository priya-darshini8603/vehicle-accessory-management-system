package org.example.service;

import java.util.List;
import org.example.entity.Accessory;

/**
 * Contract for catalog operations on accessory inventory records.
 */
public interface AccessoryService {

    Accessory addAccessory(Accessory accessory);

    List<Accessory> getAllAccessories();

    Accessory getAccessoryById(Long id);

    Accessory updateAccessory(Long id, Accessory details);

    void deleteAccessory(Long id);
}
