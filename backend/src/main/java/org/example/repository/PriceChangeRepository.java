package org.example.repository;

import org.example.entity.PriceChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceChangeRepository extends JpaRepository<PriceChange, Long> {

    // Served by idx_price_changes_accessory_changed_at.
    Page<PriceChange> findByAccessoryIdOrderByChangedAtDescIdDesc(Long accessoryId, Pageable pageable);
}
