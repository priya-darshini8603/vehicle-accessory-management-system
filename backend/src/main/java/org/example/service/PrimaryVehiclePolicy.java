package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.Vehicle;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Component;

// Keeps exactly one primary vehicle per owner, whichever API changes ownership (customer garage or
// admin vehicle CRUD). Callers must run inside a transaction.
@Component
@RequiredArgsConstructor
public class PrimaryVehiclePolicy {

    private final VehicleRepository vehicleRepository;

    // Call after the vehicle has been given to vehicle.getUser(). Its first vehicle becomes primary.
    public void onAssigned(Vehicle vehicle) {
        // Cleared first: the query below flushes this vehicle, and it must not count itself.
        vehicle.setPrimaryVehicle(false);
        if (vehicle.getUser() != null
                && !vehicleRepository.existsByUserIdAndPrimaryVehicleTrue(vehicle.getUser().getId())) {
            vehicle.setPrimaryVehicle(true);
        }
    }

    // Call when the vehicle leaves formerOwnerId (unlinked, transferred or deleted). If it was the
    // primary, the owner's oldest remaining vehicle takes over.
    public void onReleased(Vehicle vehicle, Long formerOwnerId) {
        boolean wasPrimary = vehicle.isPrimaryVehicle();
        vehicle.setPrimaryVehicle(false);
        if (wasPrimary && formerOwnerId != null) {
            vehicleRepository.findFirstByUserIdAndIdNotOrderByCreatedAtAscIdAsc(formerOwnerId, vehicle.getId())
                    .ifPresent(next -> next.setPrimaryVehicle(true));
        }
    }

    // Moves the primary flag to this vehicle within its owner's garage.
    public void makePrimary(Vehicle vehicle, Iterable<Vehicle> garage) {
        for (Vehicle other : garage) {
            other.setPrimaryVehicle(other.equals(vehicle));
        }
    }
}
