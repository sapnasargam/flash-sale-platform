package com.flashsale;

import com.flashsale.dto.response.PaymentEvent;
import com.flashsale.kafka.consumer.PaymentProcessorConsumer;
import com.flashsale.kafka.producer.PaymentEventProducer;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.PaymentRepository;
import com.flashsale.repository.ProductRepository;
import com.flashsale.service.OrderService;
import com.flashsale.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorConsumerTest {

    @Mock private PaymentService paymentService;
    @Mock private OrderService orderService;
    @Mock private PaymentEventProducer eventProducer;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks private PaymentProcessorConsumer consumer;

    @Test
    @DisplayName("Should not release inventory when duplicate failure event is received")
    void testDuplicateFailureEventDoesNotReleaseInventory() {
        PaymentEvent event = PaymentEvent.builder()
                .paymentId("PAY-1001")
                .orderId("ORD-1001")
                .build();
        when(paymentService.handlePaymentFailure(event)).thenReturn(false);

        consumer.onPaymentFailed(event);

        verify(productRepository, never()).releaseInventory(any(), any(Integer.class));
        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    @DisplayName("Should not confirm inventory when duplicate success event is received")
    void testDuplicateSuccessEventDoesNotConfirmInventory() {
        PaymentEvent event = PaymentEvent.builder()
                .paymentId("PAY-1002")
                .orderId("ORD-1002")
                .build();
        when(paymentService.handlePaymentSuccess(event)).thenReturn(false);

        consumer.onPaymentSuccess(event);

        verify(productRepository, never()).confirmInventory(any(), any(Integer.class));
        verify(orderService, never()).updateOrderStatus(any(), any());
    }
}
