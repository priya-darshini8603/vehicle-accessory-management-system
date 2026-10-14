package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.example.enums.Role;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

// @Getter/@Setter instead of @Data: Lombok's generated equals/hashCode/toString are unsafe on JPA entities
@Getter
@Setter
@ToString(exclude = "password")
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = User.UK_EMAIL, columnNames = "email"))
public class User {

    // Constraint name surfaced by the database on duplicates; used to map violations to 409 responses.
    public static final String UK_EMAIL = "uk_users_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt hash, never the raw password

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // ADMIN or CUSTOMER

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }
}