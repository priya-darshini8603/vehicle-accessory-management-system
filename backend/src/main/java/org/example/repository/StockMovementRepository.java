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
    Page<StockMovement> findByAccessoryIdOrderByOccurredAtDescIdDesc(Long accessoryId, Pageable pageable);

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
