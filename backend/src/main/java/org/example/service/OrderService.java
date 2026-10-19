package org.example.service;

import org.example.dto.OrderRequest;
import org.example.dto.OrderResponse;
import org.example.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request, String customerEmail, String idempotencyKey);

    OrderResponse getOrder(Long orderId, String customerEmail);

    PageResponse<OrderResponse> getMyOrders(String customerEmail, Pageable pageable);

    OrderResponse cancelOrder(Long orderId, String customerEmail);
}
