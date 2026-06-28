package com.flashsale.service;

import com.flashsale.dto.request.OrderRequest;
import com.flashsale.dto.response.OrderResponse;
import com.flashsale.exception.*;
import com.flashsale.model.entity.Order;
import com.flashsale.model.entity.Product;
import com.flashsale.model.enums.OrderStatus;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final IdempotencyService idempotencyService;

    @Value("${app.inventory.reservation-expiry-minutes}")
    private int reservationExpiryMinutes;

    /**
     * Create order with full concurrency safety.
     *
     * CONCURRENCY STRATEGY:
     * 1. Redis-based idempotency check (fast, prevents duplicate API calls)
     * 2. DB-level pessimistic lock on Product row (prevents race conditions)
     * 3. Atomic UPDATE with WHERE availableStock >= qty (last line of defense)
     *
     * This 3-layer approach ensures inventory NEVER goes negative,
     * even with 50 concurrent users hitting the endpoint simultaneously.
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String idempotencyKey) {

        // Build payload hash for idempotency check
        String payload = request.getUserId() + ":" + request.getProductId() + ":" + request.getQuantity();
        String payloadHash = idempotencyService.generateHash(payload);

        // Check if this is a replay of a previous request
        boolean isReplay = idempotencyService.isReplay(idempotencyKey, payloadHash);
        if (isReplay) {
            // Return the existing order for this idempotency key
            Order existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", idempotencyKey));
            log.info("Returning existing order for idempotency key: {}", idempotencyKey);
            return OrderResponse.from(existingOrder);
        }

        // LAYER 1: Fetch product with PESSIMISTIC WRITE LOCK
        // Only one thread can hold this lock → serializes concurrent access
        Product product = productRepository.findByIdWithLock(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        // Check if sale is active
        if (!product.isSaleActive()) {
            throw new SaleNotActiveException();
        }

        // Check if user already purchased this product
        boolean alreadyPurchased = orderRepository.existsByUserIdAndProductIdAndStatusNot(
                request.getUserId(), request.getProductId(), OrderStatus.FAILED);
        if (alreadyPurchased) {
            throw new DuplicateOrderException();
        }

        // Check available stock
        if (product.getAvailableStock() < request.getQuantity()) {
            throw new InsufficientInventoryException();
        }

        // LAYER 2: Atomic DB UPDATE for inventory reservation
        // WHERE availableStock >= qty → this fails if stock was taken by another thread
        int updated = productRepository.reserveInventory(product.getId(), request.getQuantity());
        if (updated == 0) {
            // Another concurrent thread took the last stock
            throw new InsufficientInventoryException();
        }

        // Calculate total amount
        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        // Create order
        Order order = Order.builder()
                .orderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .payloadHash(payloadHash)
                .reservationExpiresAt(LocalDateTime.now().plusMinutes(reservationExpiryMinutes))
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created: {} for user: {}", saved.getOrderId(), request.getUserId());

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return OrderResponse.from(order);
    }

    /**
     * Expire reservations — called by scheduler every minute.
     * Orders in PENDING state past their reservationExpiresAt are expired.
     */
    @Transactional
    public void expireReservations() {
        List<Order> expiredOrders = orderRepository.findExpiredReservations(LocalDateTime.now());

        for (Order order : expiredOrders) {
            log.info("Expiring order: {} — releasing {} units of product: {}",
                    order.getOrderId(), order.getQuantity(), order.getProductId());

            // Release reserved inventory back to available
            productRepository.releaseInventory(order.getProductId(), order.getQuantity());

            // Mark order as expired
            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
        }

        if (!expiredOrders.isEmpty()) {
            log.info("Expired {} orders and released inventory", expiredOrders.size());
        }
    }

    /**
     * Update order status — called by Kafka consumers on payment events.
     */
    @Transactional
    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.setStatus(newStatus);
        orderRepository.save(order);
        log.info("Order {} status updated to: {}", orderId, newStatus);
    }
}
