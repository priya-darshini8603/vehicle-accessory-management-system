package org.example.repository;

import org.example.entity.Vehicle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // Vehicle numbers are stored normalized (trimmed + upper-case), so callers must pass
    // Vehicle.normalizeVehicleNumber(input). Exact match keeps the unique index usable.
    Optional<Vehicle> findByVehicleNumber(String vehicleNumber);

    boolean existsByVehicleNumber(String vehicleNumber);

    // Duplicate check for updates: is the number taken by a *different* vehicle?
    boolean existsByVehicleNumberAndIdNot(String vehicleNumber, Long id);

    // Compatibility search: vehicle + all mapped accessories in a single query (no N+1).
    @Query("""
            SELECT v FROM Vehicle v
            LEFT JOIN FETCH v.accessories
            WHERE v.vehicleNumber = :vehicleNumber
            """)
    Optional<Vehicle> findByVehicleNumberWithAccessories(@Param("vehicleNumber") String vehicleNumber);

    // Single vehicle with its accessories loaded eagerly for detail/mapping responses.
    @EntityGraph(attributePaths = "accessories")
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdWithAccessories(@Param("id") Long id);

    // Second step of paginated listing: page over plain vehicles first (DB-side LIMIT/OFFSET),
    // then load accessories for just that page's IDs. Fetch-joining a collection directly in a
    // paged query would force Hibernate to paginate in memory.
    @EntityGraph(attributePaths = "accessories")
    @Query("SELECT v FROM Vehicle v WHERE v.id IN :ids")
    List<Vehicle> findAllWithAccessoriesByIdIn(@Param("ids") Collection<Long> ids);
}
