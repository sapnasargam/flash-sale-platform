package com.flashsale.service;

import com.flashsale.dto.request.PaymentRequest;
import com.flashsale.dto.response.PaymentEvent;
import com.flashsale.dto.response.PaymentResponse;
import com.flashsale.exception.InvalidPaymentException;
import com.flashsale.exception.ResourceNotFoundException;
import com.flashsale.kafka.producer.PaymentEventProducer;
import com.flashsale.model.entity.Order;
import com.flashsale.model.entity.Payment;
import com.flashsale.model.enums.OrderStatus;
import com.flashsale.model.enums.PaymentStatus;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final Set<String> ALLOWED_FORCED_STATUSES = Set.of("SUCCESS", "FAILED");

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentEventProducer eventProducer;

    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request, String idempotencyKey) {
        validateForceStatus(request.getForceStatus());

        String payload = request.getOrderId() + ":" + request.getAmount() + ":" +
                request.getPaymentMethod() + ":" + request.getForceStatus();
        String payloadHash = idempotencyService.generateHash(payload);

        boolean isReplay = idempotencyService.isReplay(idempotencyKey, payloadHash);
        if (isReplay) {
            Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", idempotencyKey));
            log.info("Payment replay for idempotency key: {}", idempotencyKey);
            return PaymentResponse.from(existing);
        }

        Order order = orderRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.getOrderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidPaymentException(
                    "Order is not in PENDING state. Current state: " + order.getStatus());
        }

        if (order.getTotalAmount().compareTo(request.getAmount()) != 0) {
            throw new InvalidPaymentException(
                    "Payment amount " + request.getAmount() +
                    " does not match order amount " + order.getTotalAmount());
        }

        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            throw new InvalidPaymentException("Payment already initiated for order: " + request.getOrderId());
        }

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .forceStatus(request.getForceStatus())
                .build();

        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAYMENT_PENDING);
        orderRepository.save(order);

        PaymentEvent event = PaymentEvent.builder()
                .paymentId(paymentId)
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .idempotencyKey(idempotencyKey)
                .forceStatus(request.getForceStatus())
                .build();

        eventProducer.publishPaymentRequested(event);
        log.info("Payment initiated: {} for order: {}", paymentId, request.getOrderId());

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public boolean handlePaymentSuccess(PaymentEvent event) {
        Payment payment = paymentRepository.findByPaymentId(event.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", event.getPaymentId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed with status: {}. Skipping duplicate.",
                    event.getPaymentId(), payment.getStatus());
            return false;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        log.info("Payment {} marked SUCCESS", event.getPaymentId());
        return true;
    }

    @Transactional
    public boolean handlePaymentFailure(PaymentEvent event) {
        Payment payment = paymentRepository.findByPaymentId(event.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", event.getPaymentId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed. Skipping duplicate failure event.", event.getPaymentId());
            return false;
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(event.getFailureReason());
        paymentRepository.save(payment);
        log.info("Payment {} marked FAILED: {}", event.getPaymentId(), event.getFailureReason());
        return true;
    }

    private void validateForceStatus(String forceStatus) {
        if (forceStatus != null && !ALLOWED_FORCED_STATUSES.contains(forceStatus.toUpperCase())) {
            throw new InvalidPaymentException("forceStatus must be SUCCESS or FAILED");
        }
    }
}
