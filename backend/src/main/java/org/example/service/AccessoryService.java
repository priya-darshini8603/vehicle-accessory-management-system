package org.example.service;

import java.util.List;
import org.example.entity.Accessory;

// Accessory business methods.
public interface AccessoryService {

    Accessory addAccessory(Accessory accessory);

    List<Accessory> getAllAccessories();

    Accessory getAccessoryById(Long id);

    Accessory updateAccessory(Long id, Accessory details);

    void deleteAccessory(Long id);
}
