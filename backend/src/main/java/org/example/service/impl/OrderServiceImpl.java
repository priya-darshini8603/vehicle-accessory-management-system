package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.OrderRequest;
import org.example.dto.OrderResponse;
import org.example.dto.PageResponse;
import org.example.dto.QuoteRequest;
import org.example.dto.QuoteResponse;
import org.example.entity.Accessory;
import org.example.entity.Order;
import org.example.entity.OrderItem;
import org.example.entity.StockMovement;
import org.example.entity.User;
import org.example.enums.InventoryProcessingStatus;
import org.example.enums.OrderStatus;
import org.example.enums.StockMovementType;
import org.example.exception.BusinessRuleException;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.OrderMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.OrderItemRepository;
import org.example.repository.OrderRepository;
import org.example.repository.StockMovementRepository;
import org.example.repository.UserRepository;
import org.example.service.OrderService;
import org.example.service.PricingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final UserRepository userRepository;
    private final AccessoryRepository accessoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PricingService pricingService;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String customerEmail, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BusinessRuleException("A non-blank Idempotency-Key of at most 128 characters is required");
        }
        Map<Long, Integer> quantities = mergeQuantities(request);
        String fingerprint = fingerprint(quantities, request.couponCode());
        User customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated customer no longer exists"));

        var prior = orderRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey.trim());
        if (prior.isPresent()) {
            if (!prior.get().getRequestFingerprint().equals(fingerprint)) {
                throw new DuplicateResourceException("Idempotency key was already used for a different order request");
            }
            return orderMapper.toResponse(prior.get());
        }

        List<Accessory> lockedAccessories = accessoryRepository.findAllByIdForUpdate(quantities.keySet());
        Map<Long, Accessory> accessories = lockedAccessories.stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));
        if (accessories.size() != quantities.size()) {
            var missing = new java.util.TreeSet<>(quantities.keySet());
            missing.removeAll(accessories.keySet());
            throw new ResourceNotFoundException("Accessories not found with ids " + missing);
        }

        QuoteResponse priced = pricingService.quoteForOrder(
                new QuoteRequest(request.items(), request.couponCode(), null), customerEmail, false,
                lockedAccessories);
        Map<Long, QuoteResponse.Line> prices = priced.lines().stream()
                .collect(Collectors.toMap(QuoteResponse.Line::accessoryId, Function.identity()));
        for (Map.Entry<Long, Integer> item : quantities.entrySet()) {
            int stock = accessories.get(item.getKey()).getStockQuantity() == null
                    ? 0 : accessories.get(item.getKey()).getStockQuantity();
            if (stock < item.getValue()) {
                throw new BusinessRuleException("Insufficient stock for accessory " + item.getKey()
                        + ": requested " + item.getValue() + ", available " + stock);
            }
        }

        Order order = new Order();
        order.setOrderNumber("ORD-" + java.util.UUID.randomUUID());
        order.setCustomer(customer);
        order.setIdempotencyKey(idempotencyKey.trim());
        order.setRequestFingerprint(fingerprint);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setInventoryProcessingStatus(InventoryProcessingStatus.DEDUCTED);
        order.setSubtotal(priced.subtotal());
        order.setDiscountAmount(priced.discountTotal());
        order.setTaxAmount(priced.taxTotal());
        order.setFinalTotal(priced.grandTotal());

        List<StockMovement> movements = new ArrayList<>(quantities.size());
        for (Map.Entry<Long, Integer> item : quantities.entrySet()) {
            Accessory accessory = accessories.get(item.getKey());
            QuoteResponse.Line price = prices.get(item.getKey());
            OrderItem orderItem = orderMapper.toItem(order, accessory, price, item.getValue());
            order.getItems().add(orderItem);

            int before = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
            int after = before - item.getValue();
            accessory.setStockQuantity(after);
            movements.add(createMovement(accessory, -item.getValue(), before, after,
                    "Order " + order.getOrderNumber(), customerEmail));
        }

        Order saved = orderRepository.save(order);
        stockMovementRepository.saveAll(movements);
        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, String customerEmail) {
        Order order = orderRepository.findByIdAndCustomerEmail(orderId, customerEmail)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(String customerEmail, Pageable pageable) {
        Page<Order> orders = orderRepository.findByCustomerEmailOrderByCreatedAtDescIdDesc(customerEmail, pageable);
        List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = orderIds.isEmpty() ? Map.of()
                : orderItemRepository.findAllByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
        return PageResponse.from(orders.map(order -> orderMapper.toResponse(order,
                itemsByOrder.getOrDefault(order.getId(), List.of()))));
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, String customerEmail) {
        Order order = orderRepository.findByIdAndCustomerEmailForUpdate(orderId, customerEmail)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        if (order.getStatus() != OrderStatus.CONFIRMED
                || order.getInventoryProcessingStatus() != InventoryProcessingStatus.DEDUCTED) {
            throw new DuplicateResourceException("Order is not eligible for cancellation");
        }

        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        List<Long> accessoryIds = items.stream().map(item -> item.getAccessory().getId()).distinct().toList();
        Map<Long, Accessory> accessories = accessoryRepository.findAllByIdForUpdate(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));
        List<StockMovement> movements = new ArrayList<>(items.size());

        for (OrderItem item : items) {
            Accessory accessory = accessories.get(item.getAccessory().getId());
            if (accessory == null) {
                throw new ResourceNotFoundException("Accessory " + item.getAccessory().getId()
                        + " for this order no longer exists");
            }
            int before = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
            long afterLong = (long) before + item.getQuantity();
            if (afterLong > Integer.MAX_VALUE) {
                throw new BusinessRuleException("Restored stock exceeds the supported maximum");
            }
            int after = (int) afterLong;
            accessory.setStockQuantity(after);
            movements.add(createMovement(accessory, item.getQuantity(), before, after,
                    "Cancellation of order " + order.getOrderNumber(), customerEmail));
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setInventoryProcessingStatus(InventoryProcessingStatus.RESTORED);
        stockMovementRepository.saveAll(movements);
        return orderMapper.toResponse(order, items);
    }

    private static Map<Long, Integer> mergeQuantities(OrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessRuleException("At least one order item is required");
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        for (QuoteRequest.Item item : request.items()) {
            if (item == null || item.accessoryId() == null || item.accessoryId() < 1
                    || item.quantity() == null || item.quantity() < 1) {
                throw new BusinessRuleException("Accessory ids and quantities must be positive");
            }
            int total = Math.addExact(quantities.getOrDefault(item.accessoryId(), 0), item.quantity());
            if (total > QuoteRequest.MAX_QUANTITY_PER_ACCESSORY) {
                throw new BusinessRuleException("Total quantity for accessory " + item.accessoryId()
                        + " must not exceed " + QuoteRequest.MAX_QUANTITY_PER_ACCESSORY);
            }
            quantities.put(item.accessoryId(), total);
        }
        return quantities;
    }

    private static String fingerprint(Map<Long, Integer> quantities, String couponCode) {
        String canonical = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","))
                + "|coupon:" + (couponCode == null ? "" : couponCode.trim().toUpperCase(java.util.Locale.ROOT));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static StockMovement createMovement(Accessory accessory, int delta, int before, int after,
                                                String note, String actor) {
        StockMovement movement = new StockMovement();
        movement.setAccessory(accessory);
        movement.setType(delta < 0 ? StockMovementType.SALE : StockMovementType.RETURN);
        movement.setQuantityDelta(delta);
        movement.setStockBefore(before);
        movement.setStockAfter(after);
        movement.setNote(note);
        movement.setChangedBy(actor);
        return movement;
    }

}
