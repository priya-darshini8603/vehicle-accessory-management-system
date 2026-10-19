package org.example.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.CompatibilityResponse;
import org.example.dto.GarageVehicleRequest;
import org.example.dto.GarageVehicleResponse;
import org.example.dto.GarageVehicleUpdateRequest;
import org.example.entity.User;
import org.example.entity.Vehicle;
import org.example.exception.BusinessRuleException;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.VehicleMapper;
import org.example.repository.UserRepository;
import org.example.repository.VehicleRepository;
import org.example.service.GarageService;
import org.example.service.PrimaryVehiclePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GarageServiceImpl implements GarageService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final VehicleMapper vehicleMapper;
    private final PrimaryVehiclePolicy primaryVehiclePolicy;
    private final int maxVehiclesPerCustomer;

    public GarageServiceImpl(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            VehicleMapper vehicleMapper,
            PrimaryVehiclePolicy primaryVehiclePolicy,
            @Value("${app.garage.max-vehicles-per-customer:10}") int maxVehiclesPerCustomer) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.vehicleMapper = vehicleMapper;
        this.primaryVehiclePolicy = primaryVehiclePolicy;
        this.maxVehiclesPerCustomer = maxVehiclesPerCustomer;
    }

    // Two queries for the whole garage: the vehicles, then all their accessory counts grouped.
    @Override
    @Transactional(readOnly = true)
    public List<GarageVehicleResponse> getGarage(String ownerEmail) {
        List<Vehicle> vehicles = vehicleRepository.findGarage(owner(ownerEmail).getId());
        if (vehicles.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = vehicleRepository
                .countAccessoriesByVehicleIds(vehicles.stream().map(Vehicle::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return vehicles.stream()
                .map(v -> vehicleMapper.toGarageResponse(v, counts.getOrDefault(v.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GarageVehicleResponse getVehicle(String ownerEmail, Long vehicleId) {
        return toResponse(findOwned(owner(ownerEmail), vehicleId));
    }

    @Override
    @Transactional
    public GarageVehicleResponse addVehicle(String ownerEmail, GarageVehicleRequest request) {
        User owner = lockedOwner(ownerEmail);
        if (vehicleRepository.countByUserId(owner.getId()) >= maxVehiclesPerCustomer) {
            throw new BusinessRuleException("A garage can hold at most " + maxVehiclesPerCustomer + " vehicles");
        }
        requireUniqueNickname(owner, request.nickname(), null);

        Optional<Vehicle> existing = vehicleRepository.findByVehicleNumber(request.vehicleNumber());
        Vehicle vehicle;
        if (existing.isPresent()) {
            vehicle = claim(existing.get(), owner, request);
        } else {
            vehicle = vehicleMapper.toEntity(request.toVehicleRequest());
            vehicle.setUser(owner);
        }
        vehicle.setNickname(request.nickname());
        primaryVehiclePolicy.onAssigned(vehicle);
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("User {} added vehicle {} to their garage", owner.getId(), saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public GarageVehicleResponse updateVehicle(String ownerEmail, Long vehicleId, GarageVehicleUpdateRequest request) {
        User owner = lockedOwner(ownerEmail);
        Vehicle vehicle = findOwned(owner, vehicleId);
        requireUniqueNickname(owner, request.nickname(), vehicleId);
        vehicle.setNickname(request.nickname());
        vehicleRepository.flush();
        return toResponse(vehicle);
    }

    @Override
    @Transactional
    public GarageVehicleResponse makePrimary(String ownerEmail, Long vehicleId) {
        User owner = lockedOwner(ownerEmail);
        Vehicle vehicle = findOwned(owner, vehicleId);
        primaryVehiclePolicy.makePrimary(vehicle, vehicleRepository.findGarage(owner.getId()));
        vehicleRepository.flush();
        return toResponse(vehicle);
    }

    @Override
    @Transactional
    public void removeVehicle(String ownerEmail, Long vehicleId) {
        User owner = lockedOwner(ownerEmail);
        Vehicle vehicle = findOwned(owner, vehicleId);
        vehicle.setUser(null);
        // The nickname is the previous owner's personal label; it must not follow the vehicle.
        vehicle.setNickname(null);
        primaryVehiclePolicy.onReleased(vehicle, owner.getId());
        log.info("User {} removed vehicle {} from their garage", owner.getId(), vehicleId);
    }

    @Override
    @Transactional(readOnly = true)
    public CompatibilityResponse getCompatibleAccessories(String ownerEmail, Long vehicleId) {
        Long ownerId = owner(ownerEmail).getId();
        return vehicleRepository.findByIdAndUserIdWithAccessories(vehicleId, ownerId)
                .map(vehicleMapper::toCompatibilityResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle", vehicleId));
    }

    // A vehicle number that already exists:
    // - already yours: 409, nothing to do
    // - owned by someone else: 409 without saying who (no ownership leak); support resolves disputes
    // - unowned (catalog entry or released by a previous owner): yours, if the details match
    private Vehicle claim(Vehicle vehicle, User owner, GarageVehicleRequest request) {
        if (vehicle.getUser() != null) {
            if (Objects.equals(vehicle.getUser().getId(), owner.getId())) {
                throw new DuplicateResourceException(
                        "Vehicle '" + request.vehicleNumber() + "' is already in your garage");
            }
            throw new DuplicateResourceException("Vehicle '" + request.vehicleNumber()
                    + "' is registered to another account. Contact support if this is your vehicle.");
        }
        // A mismatch almost always means a mistyped number; claiming it would show the wrong accessories.
        if (!vehicle.getMake().equalsIgnoreCase(request.make())
                || !vehicle.getModel().equalsIgnoreCase(request.model())
                || !vehicle.getYear().equals(request.year())) {
            throw new BusinessRuleException("Vehicle '" + request.vehicleNumber()
                    + "' is on record with different make, model or year. Check the vehicle number.");
        }
        vehicle.setUser(owner);
        return vehicle;
    }

    private void requireUniqueNickname(User owner, String nickname, Long exceptVehicleId) {
        if (nickname == null) {
            return;
        }
        boolean taken = vehicleRepository.findGarage(owner.getId()).stream()
                .anyMatch(v -> !v.getId().equals(exceptVehicleId) && nickname.equalsIgnoreCase(v.getNickname()));
        if (taken) {
            throw new DuplicateResourceException("You already have a vehicle called '" + nickname + "'");
        }
    }

    private GarageVehicleResponse toResponse(Vehicle vehicle) {
        long count = vehicleRepository.countAccessoriesByVehicleIds(List.of(vehicle.getId())).stream()
                .findFirst().map(row -> (Long) row[1]).orElse(0L);
        return vehicleMapper.toGarageResponse(vehicle, count);
    }

    private Vehicle findOwned(User owner, Long vehicleId) {
        return vehicleRepository.findByIdAndUserId(vehicleId, owner.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle", vehicleId));
    }

    private User owner(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + email + "' not found"));
    }

    // Serializes garage writes per user; see UserRepository.findByEmailForUpdate.
    private User lockedOwner(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + email + "' not found"));
    }
}
