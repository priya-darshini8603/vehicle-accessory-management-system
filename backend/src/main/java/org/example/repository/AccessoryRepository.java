package org.example.repository;

import org.example.entity.Accessory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessoryRepository extends JpaRepository<Accessory, Long> {

    @Query("SELECT COUNT(v) > 0 FROM Vehicle v JOIN v.accessories a WHERE a.id = :accessoryId")
    boolean isAccessoryAssignedToVehicle(@Param("accessoryId") Long accessoryId);
}