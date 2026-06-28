package com.flashsale.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Inventory Expiry Scheduler
 *
 * Runs every 30 seconds and checks for orders in PENDING state
 * whose reservationExpiresAt has passed (2 minutes default).
 * Expired orders → inventory released + order marked EXPIRED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryExpiryScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 30_000)  // Every 30 seconds
    public void expireReservations() {
        log.debug("Running inventory expiry check...");
        orderService.expireReservations();
    }
}
