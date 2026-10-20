package org.example.repository;

import org.example.entity.Discount;
import org.example.enums.DiscountScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {

    // Codes are stored normalized, so callers must pass Discount.normalizeCode(input).
    Optional<Discount> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    // Every automatic promotion that could apply to a cart, in one query regardless of cart size.
    // Categories are stored lower-case (DiscountRequest), so callers must pass lower-cased values and the
    // comparison stays on the raw column, which idx_discounts_category can serve.
    @Query("""
            SELECT d FROM Discount d
            WHERE d.active = true AND d.code IS NULL
              AND (d.validFrom IS NULL OR d.validFrom <= :now)
              AND (d.validTo IS NULL OR d.validTo > :now)
              AND (d.scope = :all
                   OR (d.scope = :category AND d.category IN :categories)
                   OR (d.scope = :accessory AND d.accessory.id IN :accessoryIds))
            """)
    List<Discount> findLiveAutomaticDiscounts(
            @Param("now") Instant now,
            @Param("categories") Collection<String> categories,
            @Param("accessoryIds") Collection<Long> accessoryIds,
            @Param("all") DiscountScope all,
            @Param("category") DiscountScope category,
            @Param("accessory") DiscountScope accessory);

    default List<Discount> findLiveAutomaticDiscounts(
            Instant now, Collection<String> categories, Collection<Long> accessoryIds) {
        return findLiveAutomaticDiscounts(now, categories, accessoryIds,
                DiscountScope.ALL, DiscountScope.CATEGORY, DiscountScope.ACCESSORY);
    }

    // Discounts aimed at one accessory are meaningless once it is deleted (and would block the delete).
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Discount d WHERE d.accessory.id = :accessoryId")
    int deleteByAccessoryId(@Param("accessoryId") Long accessoryId);
}
