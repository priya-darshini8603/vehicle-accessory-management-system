package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

// Audit trail of accessory price changes: who changed what, and when.
// accessoryId is a plain column, not a foreign key, so the history survives deleting the accessory.
@Entity
@Table(
        name = "price_changes",
        indexes = @Index(name = "idx_price_changes_accessory_changed_at", columnList = "accessory_id, changed_at"))
@Getter
@Setter
@NoArgsConstructor
public class PriceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "accessory_id", nullable = false)
    private Long accessoryId;

    // Null for the initial price set when the accessory was created.
    @Column(name = "old_price", precision = 12, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "old_cost_price", precision = 12, scale = 2)
    private BigDecimal oldCostPrice;

    @Column(name = "new_cost_price", precision = 12, scale = 2)
    private BigDecimal newCostPrice;

    @Column(name = "changed_by", nullable = false, length = 254)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
