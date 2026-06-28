package com.flashsale.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.payment-requested}")
    private String paymentRequestedTopic;

    @Value("${app.kafka.topics.payment-success}")
    private String paymentSuccessTopic;

    @Value("${app.kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Value("${app.kafka.topics.payment-dlq}")
    private String paymentDlqTopic;

    @Bean
    public NewTopic paymentRequestedTopic() {
        return TopicBuilder.name(paymentRequestedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentSuccessTopic() {
        return TopicBuilder.name(paymentSuccessTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(paymentFailedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentDlqTopic() {
        // DLQ: 1 partition, longer retention for investigation
        return TopicBuilder.name(paymentDlqTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
