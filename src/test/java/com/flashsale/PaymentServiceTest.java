package com.flashsale;

import com.flashsale.dto.request.PaymentRequest;
import com.flashsale.dto.response.PaymentEvent;
import com.flashsale.dto.response.PaymentResponse;
import com.flashsale.exception.InvalidPaymentException;
import com.flashsale.kafka.producer.PaymentEventProducer;
import com.flashsale.model.entity.Order;
import com.flashsale.model.entity.Payment;
import com.flashsale.model.enums.OrderStatus;
import com.flashsale.model.enums.PaymentStatus;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.PaymentRepository;
import com.flashsale.service.IdempotencyService;
import com.flashsale.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private PaymentEventProducer eventProducer;

    @InjectMocks private PaymentService paymentService;

    private Order order;
    private PaymentRequest request;

    @BeforeEach
    void setUp() {
        order = Order.builder()
                .orderId("ORD-1001")
                .userId(101L)
                .productId(1L)
                .quantity(2)
                .totalAmount(new BigDecimal("200000"))
                .status(OrderStatus.PENDING)
                .build();

        request = PaymentRequest.builder()
                .orderId("ORD-1001")
                .amount(new BigDecimal("200000"))
                .paymentMethod("UPI")
                .forceStatus("SUCCESS")
                .build();
    }

    @Test
    @DisplayName("Should initiate payment, update order, and publish Kafka event")
    void testInitiatePayment_success() {
        when(idempotencyService.generateHash(anyString())).thenReturn("hash");
        when(idempotencyService.isReplay("PAY-KEY-1", "hash")).thenReturn(false);
        when(orderRepository.findByOrderId("ORD-1001")).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId("ORD-1001")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.initiatePayment(request, "PAY-KEY-1");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventProducer).publishPaymentRequested(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getForceStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should reject invalid forced payment status")
    void testInitiatePayment_invalidForceStatus() {
        request.setForceStatus("MAYBE");

        assertThatThrownBy(() -> paymentService.initiatePayment(request, "PAY-KEY-2"))
                .isInstanceOf(InvalidPaymentException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventProducer, never()).publishPaymentRequested(any(PaymentEvent.class));
    }

    @Test
    @DisplayName("Should not process duplicate success event twice")
    void testHandlePaymentSuccess_duplicateEvent() {
        Payment payment = Payment.builder()
                .paymentId("PAY-1001")
                .orderId("ORD-1001")
                .amount(new BigDecimal("200000"))
                .paymentMethod("UPI")
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("PAY-KEY-3")
                .build();
        when(paymentRepository.findByPaymentId("PAY-1001")).thenReturn(Optional.of(payment));

        boolean processed = paymentService.handlePaymentSuccess(
                PaymentEvent.builder().paymentId("PAY-1001").orderId("ORD-1001").build());

        assertThat(processed).isFalse();
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
