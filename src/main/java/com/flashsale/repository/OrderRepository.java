package com.flashsale.repository;

import com.flashsale.model.entity.Order;
import com.flashsale.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderId(String orderId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    // Check if user already purchased this product (one purchase per user per product)
    boolean existsByUserIdAndProductIdAndStatusNot(Long userId, Long productId, OrderStatus status);

    // Find expired reservations for cleanup scheduler
    @Query("SELECT o FROM Order o WHERE o.status IN ('PENDING', 'PAYMENT_PENDING') " +
           "AND o.reservationExpiresAt < :now")
    List<Order> findExpiredReservations(@Param("now") LocalDateTime now);
}
