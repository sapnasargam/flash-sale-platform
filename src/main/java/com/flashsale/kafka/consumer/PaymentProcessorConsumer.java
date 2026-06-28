package com.flashsale.kafka.consumer;

import com.flashsale.dto.response.PaymentEvent;
import com.flashsale.kafka.producer.PaymentEventProducer;
import com.flashsale.model.enums.OrderStatus;
import com.flashsale.repository.PaymentRepository;
import com.flashsale.service.OrderService;
import com.flashsale.service.PaymentService;
import com.flashsale.repository.ProductRepository;
import com.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

/**
 * Payment Processor Consumer — simulates an external payment gateway.
 *
 * FLOW:
 * 1. Listens to payment.requested topic
 * 2. Simulates payment processing (random 80% success / 20% failure, or forced)
 * 3. Publishes result to payment.success or payment.failed topic
 * 4. Updates order and inventory accordingly
 *
 * RETRY & DLQ:
 * @RetryableTopic automatically retries 3 times with exponential backoff.
 * After all retries exhausted → moved to DLQ topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorConsumer {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PaymentEventProducer eventProducer;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    private static final Random RANDOM = new Random();

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlq"
    )
    @KafkaListener(
        topics = "${app.kafka.topics.payment-requested}",
        groupId = "payment-processor-group"
    )
    @Transactional
    public void processPaymentRequest(PaymentEvent event) {
        log.info("Processing payment request for orderId: {}, paymentId: {}",
                event.getOrderId(), event.getPaymentId());

        // Idempotency: check if this payment was already processed
        // (handles duplicate Kafka message delivery)
        var payment = paymentRepository.findByPaymentId(event.getPaymentId());
        if (payment.isPresent() && !payment.get().getStatus().name().equals("PENDING")) {
            log.info("Payment {} already processed as {}. Skipping duplicate Kafka event.",
                    event.getPaymentId(), payment.get().getStatus());
            return;
        }

        // Determine outcome: forced or random
        boolean isSuccess = determineOutcome(event.getForceStatus());

        if (isSuccess) {
            handleSuccess(event);
        } else {
            handleFailure(event, "Payment declined by gateway");
        }
    }

    /**
     * DLQ handler — called when all retries are exhausted.
     * Logs for manual investigation and marks order as FAILED.
     */
    @DltHandler
    public void handleDlq(PaymentEvent event) {
        log.error("Payment event moved to DLQ after all retries. orderId: {}, paymentId: {}",
                event.getOrderId(), event.getPaymentId());

        // Release inventory and mark order as FAILED
        try {
            var order = orderRepository.findByOrderId(event.getOrderId());
            order.ifPresent(o -> {
                productRepository.releaseInventory(o.getProductId(), o.getQuantity());
                orderService.updateOrderStatus(event.getOrderId(), OrderStatus.FAILED);
            });
        } catch (Exception ex) {
            log.error("Failed to handle DLQ cleanup for orderId: {}", event.getOrderId(), ex);
        }

        // Publish to custom DLQ topic for monitoring
        eventProducer.publishToDlq(event);
    }

    // ─── Kafka Listener for payment.success ───────────────────────────
    @KafkaListener(
        topics = "${app.kafka.topics.payment-success}",
        groupId = "order-update-group"
    )
    @Transactional
    public void onPaymentSuccess(PaymentEvent event) {
        log.info("Payment success event received for orderId: {}", event.getOrderId());

        boolean processed = paymentService.handlePaymentSuccess(event);
        if (!processed) {
            return;
        }

        // Update order status → CONFIRMED
        orderService.updateOrderStatus(event.getOrderId(), OrderStatus.CONFIRMED);

        // Confirm inventory (remove from reserved)
        var order = orderRepository.findByOrderId(event.getOrderId());
        order.ifPresent(o -> productRepository.confirmInventory(o.getProductId(), o.getQuantity()));

        log.info("Order {} confirmed after payment success", event.getOrderId());
    }

    // ─── Kafka Listener for payment.failed ────────────────────────────
    @KafkaListener(
        topics = "${app.kafka.topics.payment-failed}",
        groupId = "order-update-group"
    )
    @Transactional
    public void onPaymentFailed(PaymentEvent event) {
        log.info("Payment failed event received for orderId: {}", event.getOrderId());

        boolean processed = paymentService.handlePaymentFailure(event);
        if (!processed) {
            return;
        }

        // Release reserved inventory
        var order = orderRepository.findByOrderId(event.getOrderId());
        order.ifPresent(o -> productRepository.releaseInventory(o.getProductId(), o.getQuantity()));

        // Update order status → PAYMENT_FAILED
        orderService.updateOrderStatus(event.getOrderId(), OrderStatus.PAYMENT_FAILED);

        log.info("Inventory released and order {} marked as PAYMENT_FAILED", event.getOrderId());
    }

    // ─── Private helpers ──────────────────────────────────────────────

    private boolean determineOutcome(String forceStatus) {
        if (forceStatus != null) {
            return "SUCCESS".equalsIgnoreCase(forceStatus);
        }
        // Mode 1: Random — 80% success, 20% failure
        return RANDOM.nextInt(100) < 80;
    }

    private void handleSuccess(PaymentEvent event) {
        PaymentEvent successEvent = PaymentEvent.builder()
                .paymentId(event.getPaymentId())
                .orderId(event.getOrderId())
                .amount(event.getAmount())
                .paymentMethod(event.getPaymentMethod())
                .status("SUCCESS")
                .idempotencyKey(event.getIdempotencyKey())
                .build();

        eventProducer.publishPaymentSuccess(successEvent);
        log.info("Payment {} processed as SUCCESS", event.getPaymentId());
    }

    private void handleFailure(PaymentEvent event, String reason) {
        PaymentEvent failedEvent = PaymentEvent.builder()
                .paymentId(event.getPaymentId())
                .orderId(event.getOrderId())
                .amount(event.getAmount())
                .paymentMethod(event.getPaymentMethod())
                .status("FAILED")
                .failureReason(reason)
                .idempotencyKey(event.getIdempotencyKey())
                .build();

        eventProducer.publishPaymentFailed(failedEvent);
        log.info("Payment {} processed as FAILED: {}", event.getPaymentId(), reason);
    }
}
