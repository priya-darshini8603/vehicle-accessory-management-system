package org.example.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "vehicles",
        uniqueConstraints = @UniqueConstraint(
                name = Vehicle.UK_VEHICLE_NUMBER,
                columnNames = "vehicle_number"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    // Constraint name surfaced by the database on duplicates; used to map violations to 409 responses.
    public static final String UK_VEHICLE_NUMBER = "uk_vehicles_vehicle_number";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Fulfills the "Vehicle number must be unique" requirement (VIN).
    // The unique constraint is backed by a B-tree index, so lookups by number are indexed.
    @Column(name = "vehicle_number", nullable = false, length = 32)
    private String vehicleNumber;

    @Column(nullable = false, length = 60)
    private String make;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(nullable = false)
    private Integer year;

    // Links the vehicle to a specific User (Customer) — optional
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // The Many-To-Many Mapping table (vehicle_accessories).
    // LAZY to avoid N+1 on listings; fetch explicitly with JOIN FETCH / @EntityGraph when needed.
    // No cascade: accessories have an independent lifecycle and must already exist.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vehicle_accessories",
            joinColumns = @JoinColumn(name = "vehicle_id"),
            inverseJoinColumns = @JoinColumn(name = "accessory_id")
    )
    private Set<Accessory> accessories = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Canonical form of a vehicle number: trimmed and upper-cased.
    // Shared by persistence callbacks and lookups so searches are case-insensitive.
    public static String normalizeVehicleNumber(String vehicleNumber) {
        return vehicleNumber == null ? null : vehicleNumber.trim().toUpperCase(Locale.ROOT);
    }

    @PrePersist
    @PreUpdate
    private void normalize() {
        this.vehicleNumber = normalizeVehicleNumber(this.vehicleNumber);
    }

    //  Links an accessory, keeping both sides of the bidirectional association in sync.
    public void addAccessory(Accessory accessory) {
        accessories.add(accessory);
        accessory.getVehicles().add(this);
    }


    // Unlinks an accessory, keeping both sides of the bidirectional association in sync.
    public void removeAccessory(Accessory accessory) {
        accessories.remove(accessory);
        accessory.getVehicles().remove(this);
    }


    // Unlinks all accessories (used before delete and for bulk replacement).
    public void clearAccessories() {
        for (Accessory accessory : new HashSet<>(accessories)) {
            removeAccessory(accessory);
        }
    }

    // Identity-based equality that is safe for Hibernate proxies and transient instances.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle other)) return false;
        return id != null && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return Vehicle.class.hashCode();
    }
}