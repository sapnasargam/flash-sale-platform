package com.flashsale.kafka.producer;

import com.flashsale.dto.response.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${app.kafka.topics.payment-requested}")
    private String paymentRequestedTopic;

    @Value("${app.kafka.topics.payment-success}")
    private String paymentSuccessTopic;

    @Value("${app.kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Value("${app.kafka.topics.payment-dlq}")
    private String paymentDlqTopic;

    /**
     * Publish payment.requested event when user initiates payment.
     * Key = orderId ensures all events for same order go to same partition (ordering guarantee).
     */
    public void publishPaymentRequested(PaymentEvent event) {
        publishEvent(paymentRequestedTopic, event.getOrderId(), event);
    }

    public void publishPaymentSuccess(PaymentEvent event) {
        publishEvent(paymentSuccessTopic, event.getOrderId(), event);
    }

    public void publishPaymentFailed(PaymentEvent event) {
        publishEvent(paymentFailedTopic, event.getOrderId(), event);
    }

    public void publishToDlq(PaymentEvent event) {
        publishEvent(paymentDlqTopic, event.getOrderId(), event);
        log.error("Event moved to DLQ for orderId: {}", event.getOrderId());
    }

    private void publishEvent(String topic, String key, PaymentEvent event) {
        CompletableFuture<SendResult<String, PaymentEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic: {} for orderId: {}, error: {}",
                        topic, event.getOrderId(), ex.getMessage());
            } else {
                log.info("Published event to topic: {} partition: {} offset: {} for orderId: {}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getOrderId());
            }
        });
    }
}
