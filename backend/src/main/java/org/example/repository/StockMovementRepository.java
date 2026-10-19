package org.example.repository;

import org.example.entity.StockMovement;
import org.example.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @EntityGraph(attributePaths = "accessory")
    @Query("""
            select m from StockMovement m
            where m.accessory.id = :accessoryId
              and (:type is null or m.type = :type)
              and (:from is null or m.occurredAt >= :from)
              and (:to is null or m.occurredAt <= :to)
            """)
    Page<StockMovement> findHistory(@Param("accessoryId") Long accessoryId,
                                    @Param("type") StockMovementType type,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);

    @EntityGraph(attributePaths = "accessory")
    List<StockMovement> findTop20ByOrderByOccurredAtDescIdDesc();

    boolean existsByAccessoryId(Long accessoryId);

    @Query("""
            select m.accessory.id as accessoryId, sum(-m.quantityDelta) as unitsSold
            from StockMovement m
            where m.type = :type and m.occurredAt >= :since
            group by m.accessory.id
            """)
    List<SalesAggregate> sumSalesSince(@Param("type") StockMovementType type, @Param("since") Instant since);

    interface SalesAggregate {
        Long getAccessoryId();
        Long getUnitsSold();
    }
}
