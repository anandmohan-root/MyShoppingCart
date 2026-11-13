package com.myshoppingcart.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** Catalog only publishes; it consumes nothing, so it needs no error handler. */
@Configuration
class KafkaConfig {

    @Bean
    KafkaAdmin.NewTopics topics() {
        return new KafkaAdmin.NewTopics(TopicBuilder.name("product-created").build());
    }
}
