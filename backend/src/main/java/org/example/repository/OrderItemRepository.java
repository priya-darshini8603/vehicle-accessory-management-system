package org.example.repository;

import org.example.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = "accessory")
    List<OrderItem> findAllByOrderId(Long orderId);

    @EntityGraph(attributePaths = "accessory")
    List<OrderItem> findAllByOrderIdIn(Collection<Long> orderIds);
}
