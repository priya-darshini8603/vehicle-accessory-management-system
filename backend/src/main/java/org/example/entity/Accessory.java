package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "accessories",
        uniqueConstraints = {
                @jakarta.persistence.UniqueConstraint(
                        name = "uk_accessory_name_category",
                        columnNames = {"name", "category"}
                )
        },
        indexes = {
                @Index(name = "idx_accessories_category", columnList = "category"),
                @Index(name = "idx_accessories_name", columnList = "name"),
                @Index(name = "idx_accessories_stock_quantity", columnList = "stock_quantity"),
                @Index(name = "idx_accessories_min_stock", columnList = "min_stock_threshold")
        }
)
@Check(name = "ck_accessories_nonnegative_stock", constraints = "stock_quantity >= 0")
@Getter
@Setter
@NoArgsConstructor
public class Accessory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    // Base selling price before discounts and tax.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // What the accessory costs us. Discounts never take the selling price below it (margin floor).
    // Nullable so existing rows stay valid; null means no floor other than zero.
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    // GST rate in percent. Null means the default rate (app.pricing.default-tax-rate).
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(length = 1000)
    private String description;

    @Column(name = "stock_quantity", nullable = false, columnDefinition = "integer default 0")
    private Integer stockQuantity = 0;

    @Column(name = "min_stock_threshold", nullable = false, columnDefinition = "integer default 0")
    private Integer minStockThreshold = 0;

    @Column(name = "lead_time_days", nullable = false, columnDefinition = "integer default 7")
    private Integer leadTimeDays = 7;

    @ManyToMany(mappedBy = "accessories", fetch = FetchType.LAZY)
    private Set<Vehicle> vehicles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Accessory other)) return false;
        return id != null && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Accessory.class.hashCode();
    }
}