package com.myshoppingcart.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Topics this service publishes or consumes, and what happens to a message it cannot process:
 * it is retried twice, one second apart, then moved to "&lt;topic&gt;-dlt" so the rest of the
 * topic keeps flowing. Spring Boot applies the error handler bean to every listener.
 */
@Configuration
class KafkaConfig {

    @Bean
    KafkaAdmin.NewTopics topics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("order-created").build(),
                TopicBuilder.name("refund-requested").build(),
                TopicBuilder.name("order-cancelled").build(),
                TopicBuilder.name("stock-rejected").build(),
                TopicBuilder.name("stock-rejected-dlt").build(),
                TopicBuilder.name("payment-completed").build(),
                TopicBuilder.name("payment-completed-dlt").build(),
                TopicBuilder.name("payment-declined").build(),
                TopicBuilder.name("payment-declined-dlt").build());
    }

    @Bean
    CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(1_000, 2));
    }
}
