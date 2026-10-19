package org.example.repository;

import jakarta.persistence.LockModeType;
import org.example.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.accessory"})
    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    @EntityGraph(attributePaths = {"items", "items.accessory"})
    Optional<Order> findByIdAndCustomerEmail(Long id, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.customer.email = :email")
    Optional<Order> findByIdAndCustomerEmailForUpdate(@Param("id") Long id, @Param("email") String email);

    Page<Order> findByCustomerEmailOrderByCreatedAtDescIdDesc(String email, Pageable pageable);
}
