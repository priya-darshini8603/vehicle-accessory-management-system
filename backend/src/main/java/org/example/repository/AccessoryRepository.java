package org.example.repository;

import org.example.entity.Accessory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessoryRepository extends JpaRepository<Accessory, Long> {

    @Query("SELECT COUNT(v) > 0 FROM Vehicle v JOIN v.accessories a WHERE a.id = :accessoryId")
    boolean isAccessoryAssignedToVehicle(@Param("accessoryId") Long accessoryId);

    // All accessories mapped to a vehicle, in caller-defined order.
    @Query("SELECT a FROM Accessory a JOIN a.vehicles v WHERE v.id = :vehicleId")
    List<Accessory> findAllByVehicleId(@Param("vehicleId") Long vehicleId, Sort sort);

    // Removes every vehicle mapping for an accessory in one statement, so it can be deleted
    // cleanly without loading all linked vehicles. Clears the persistence context afterwards
    // to avoid stale in-memory associations.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM vehicle_accessories WHERE accessory_id = :accessoryId", nativeQuery = true)
    int deleteVehicleMappings(@Param("accessoryId") Long accessoryId);
}