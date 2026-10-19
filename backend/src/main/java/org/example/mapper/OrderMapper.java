package org.example.mapper;

import org.example.dto.OrderResponse;
import org.example.dto.QuoteResponse;
import org.example.entity.Accessory;
import org.example.entity.Order;
import org.example.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderItem toItem(Order order, Accessory accessory, QuoteResponse.Line price, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setAccessory(accessory);
        item.setAccessoryName(price.name());
        item.setQuantity(quantity);
        item.setUnitPrice(price.unitPrice());
        item.setUnitDiscount(price.unitDiscount());
        item.setDiscountName(price.appliedDiscount() == null ? null : price.appliedDiscount().name());
        item.setTaxRate(price.taxRate());
        item.setSubtotal(price.subtotal());
        item.setDiscountAmount(price.discountTotal());
        item.setTaxAmount(price.taxAmount());
        item.setLineTotal(price.total());
        return item;
    }

    public OrderResponse toResponse(Order order) {
        return toResponse(order, order.getItems());
    }

    public OrderResponse toResponse(Order order, List<OrderItem> items) {
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getInventoryProcessingStatus(), order.getSubtotal(), order.getDiscountAmount(),
                order.getTaxAmount(), order.getFinalTotal(), order.getCreatedAt(), order.getUpdatedAt(),
                items.stream().map(this::toItemResponse).toList());
    }

    private OrderResponse.Item toItemResponse(OrderItem item) {
        return new OrderResponse.Item(item.getAccessory().getId(), item.getAccessoryName(),
                item.getQuantity(), item.getUnitPrice(), item.getUnitDiscount(), item.getDiscountName(),
                item.getTaxRate(), item.getSubtotal(), item.getDiscountAmount(), item.getTaxAmount(),
                item.getLineTotal());
    }
}
