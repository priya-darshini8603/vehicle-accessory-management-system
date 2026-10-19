package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.enums.DiscountScope;
import org.example.enums.DiscountType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

// A price reduction. Without a code it applies automatically (a promotion); with a code it applies
// only when the customer enters it (a coupon). PriceCalculator decides which discount wins per line.
@Entity
@Table(
        name = "discounts",
        uniqueConstraints = @UniqueConstraint(name = Discount.UK_CODE, columnNames = "code"),
        indexes = {
                // The quote query filters active discounts by scope target.
                @Index(name = "idx_discounts_active_scope", columnList = "active, scope"),
                @Index(name = "idx_discounts_accessory_id", columnList = "accessory_id"),
                @Index(name = "idx_discounts_category", columnList = "category")
        })
@Getter
@Setter
@NoArgsConstructor
public class Discount {

    // Constraint name surfaced by the database on duplicates; used to map violations to 409 responses.
    public static final String UK_CODE = "uk_discounts_code";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Coupon code, stored upper-case. Null for automatic promotions (unique allows many NULLs).
    @Column(length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountScope scope;

    // Target when scope = CATEGORY (matched case-insensitively against Accessory.category).
    @Column(length = 50)
    private String category;

    // Target when scope = ACCESSORY.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accessory_id")
    private Accessory accessory;

    // Upper bound on the amount a PERCENTAGE discount takes off one unit ("20% off, up to ₹500").
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    // Bulk pricing: the discount applies only when at least this many units are bought.
    @Column(name = "min_quantity", nullable = false)
    private int minQuantity = 1;

    // Validity window; null means open-ended on that side. validTo is exclusive.
    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(nullable = false)
    private boolean active = true;

    // Optimistic locking: two admins editing the same discount at once get a 409 instead of a lost update.
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isCoupon() {
        return code != null;
    }

    // Active and inside its validity window at the given instant.
    public boolean isLiveAt(Instant now) {
        return active
                && (validFrom == null || !now.isBefore(validFrom))
                && (validTo == null || now.isBefore(validTo));
    }

    @PrePersist
    @PreUpdate
    private void normalize() {
        this.code = normalizeCode(this.code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Discount other)) return false;
        return id != null && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Discount.class.hashCode();
    }
}
