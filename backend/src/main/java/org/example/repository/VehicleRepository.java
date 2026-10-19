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

    // Model search fetches mapped accessories in the same query; '!' is the LIKE escape character.
    @Query("""
         SELECT DISTINCT v FROM Vehicle v
         LEFT JOIN FETCH v.accessories
         WHERE LOWER(v.model) LIKE LOWER(:modelNamePattern) ESCAPE '!' 
         """)
    List<Vehicle> findByModelNamePatternWithAccessories(@Param("modelNamePattern") String modelNamePattern);


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

    // --- Customer garage (served by idx_vehicles_user_id) ---------------------------------

    // Primary vehicle first, then in the order they were added.
    @Query("""
            SELECT v FROM Vehicle v
            WHERE v.user.id = :userId
            ORDER BY v.primaryVehicle DESC, v.createdAt ASC, v.id ASC
            """)
    List<Vehicle> findGarage(@Param("userId") Long userId);

    // Ownership is part of the WHERE clause, so another customer's vehicle is simply "not found".
    Optional<Vehicle> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "accessories")
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id AND v.user.id = :userId")
    Optional<Vehicle> findByIdAndUserIdWithAccessories(@Param("id") Long id, @Param("userId") Long userId);

    @EntityGraph(attributePaths = "accessories")
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id AND v.user.email = :email")
    Optional<Vehicle> findByIdAndOwnerEmailWithAccessories(@Param("id") Long id, @Param("email") String email);

    long countByUserId(Long userId);

    boolean existsByUserIdAndPrimaryVehicleTrue(Long userId);

    // Oldest remaining vehicle of an owner, used to hand over the primary flag.
    Optional<Vehicle> findFirstByUserIdAndIdNotOrderByCreatedAtAscIdAsc(Long userId, Long excludedId);

    // Compatible-accessory counts for many vehicles in one grouped query instead of one per vehicle.
    // Each row is [vehicleId, count].
    @Query("""
            SELECT v.id, COUNT(a.id) FROM Vehicle v LEFT JOIN v.accessories a
            WHERE v.id IN :ids GROUP BY v.id
            """)
    List<Object[]> countAccessoriesByVehicleIds(@Param("ids") Collection<Long> ids);
}
