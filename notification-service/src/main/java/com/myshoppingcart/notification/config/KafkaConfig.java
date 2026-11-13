package com.myshoppingcart.notification.config;

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
 * Topics this service consumes, and what happens to a message it cannot process. Retries are
 * longer than in the other services (5 times, 2 seconds apart): an event can arrive just before
 * its order's OrderCreated has been read, and a mail server can be briefly unavailable. After that
 * the message moves to "&lt;topic&gt;-dlt".
 */
@Configuration
class KafkaConfig {

    @Bean
    KafkaAdmin.NewTopics topics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("order-created").build(),
                TopicBuilder.name("order-created-dlt").build(),
                TopicBuilder.name("payment-completed").build(),
                TopicBuilder.name("payment-completed-dlt").build(),
                TopicBuilder.name("payment-declined").build(),
                TopicBuilder.name("payment-declined-dlt").build(),
                TopicBuilder.name("stock-rejected").build(),
                TopicBuilder.name("stock-rejected-dlt").build(),
                TopicBuilder.name("order-cancelled").build(),
                TopicBuilder.name("order-cancelled-dlt").build(),
                TopicBuilder.name("refund-requested").build(),
                TopicBuilder.name("refund-requested-dlt").build());
    }

    @Bean
    CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(2_000, 5));
    }
}
