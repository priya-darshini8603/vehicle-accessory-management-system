package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.PageResponse;
import org.example.dto.VehicleRequest;
import org.example.dto.VehicleResponse;
import org.example.entity.Accessory;
import org.example.entity.User;
import org.example.entity.Vehicle;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.VehicleMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.UserRepository;
import org.example.repository.VehicleRepository;
import org.example.service.VehicleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final AccessoryRepository accessoryRepository;
    private final UserRepository userRepository;
    private final VehicleMapper vehicleMapper;

    @Override
    @Transactional
    public VehicleResponse createVehicle(VehicleRequest request) {
        if (vehicleRepository.existsByVehicleNumber(request.vehicleNumber())) {
            throw duplicateNumber(request.vehicleNumber());
        }
        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setUser(resolveOwner(request.userId()));
        return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
    }

    // Pages over plain vehicles in the database, then loads accessories for just that page in one
    // query. Fetch-joining a collection in the paged query itself would make Hibernate paginate in memory.
    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> getVehicles(Pageable pageable) {
        Page<Vehicle> page = vehicleRepository.findAll(pageable);
        if (page.hasContent()) {
            List<Long> ids = page.getContent().stream().map(Vehicle::getId).toList();
            // Same persistence context, so this initializes the accessories of the page's entities.
            vehicleRepository.findAllWithAccessoriesByIdIn(ids);
        }
        return PageResponse.from(page, vehicleMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id) {
        return vehicleMapper.toResponse(findWithAccessories(id));
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(Long id, VehicleRequest request) {
        Vehicle vehicle = findWithAccessories(id);
        if (vehicleRepository.existsByVehicleNumberAndIdNot(request.vehicleNumber(), id)) {
            throw duplicateNumber(request.vehicleNumber());
        }
        vehicleMapper.updateEntity(vehicle, request);
        // PUT replaces the full resource, so an omitted userId clears the owner.
        vehicle.setUser(resolveOwner(request.userId()));
        // Flush so @UpdateTimestamp is applied (and constraint violations surface) before responding.
        vehicleRepository.flush();
        return vehicleMapper.toResponse(vehicle);
    }

    // Vehicle owns the association, so Hibernate deletes its vehicle_accessories rows with it;
    // the accessories themselves are kept.
    @Override
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle", id));
        vehicleRepository.delete(vehicle);
    }

    // Idempotent: mapping an already-mapped accessory is a no-op.
    @Override
    @Transactional
    public VehicleResponse addAccessory(Long vehicleId, Long accessoryId) {
        Vehicle vehicle = findWithAccessories(vehicleId);
        Accessory accessory = accessoryRepository.findById(accessoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Accessory", accessoryId));
        if (!vehicle.getAccessories().contains(accessory)) {
            vehicle.addAccessory(accessory);
        }
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse removeAccessory(Long vehicleId, Long accessoryId) {
        Vehicle vehicle = findWithAccessories(vehicleId);
        Accessory accessory = vehicle.getAccessories().stream()
                .filter(a -> a.getId().equals(accessoryId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Accessory with id " + accessoryId + " is not mapped to vehicle with id " + vehicleId));
        vehicle.removeAccessory(accessory);
        return vehicleMapper.toResponse(vehicle);
    }

    // Replaces the vehicle's mappings with exactly the given set; an empty set clears them.
    // Fails as a whole (nothing changes) if any id does not exist.
    @Override
    @Transactional
    public VehicleResponse replaceAccessories(Long vehicleId, Set<Long> accessoryIds) {
        Vehicle vehicle = findWithAccessories(vehicleId);

        Map<Long, Accessory> found = accessoryRepository.findAllById(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));
        if (found.size() != accessoryIds.size()) {
            Set<Long> missing = new TreeSet<>(accessoryIds);
            missing.removeAll(found.keySet());
            throw new ResourceNotFoundException("Accessories not found with ids " + missing);
        }

        // Only the owning side is updated here: syncing each accessory's inverse collection would
        // lazily load one collection per accessory, and nothing reads it in this transaction.
        vehicle.getAccessories().removeIf(existing -> !found.containsKey(existing.getId()));
        vehicle.getAccessories().addAll(found.values());
        return vehicleMapper.toResponse(vehicle);
    }

    private Vehicle findWithAccessories(Long id) {
        return vehicleRepository.findByIdWithAccessories(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle", id));
    }

    private User resolveOwner(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private static DuplicateResourceException duplicateNumber(String vehicleNumber) {
        return new DuplicateResourceException("Vehicle with number '" + vehicleNumber + "' already exists");
    }
}
