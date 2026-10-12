package org.example.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "accessories")
public class Accessory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private Double price;
    private Integer stockQuantity;

    // Inverse side of the Many-to-Many relationship
    @ManyToMany(mappedBy = "accessories")
    private Set<Vehicle> vehicles = new HashSet<>();
}