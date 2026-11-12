package com.myshoppingcart.payment.adapter.out.messaging;

import com.myshoppingcart.payment.application.PaymentEvents;
import com.myshoppingcart.payment.domain.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
class KafkaPaymentEvents implements PaymentEvents {

    private static final String TOPIC = "payment-completed";

    /** The PaymentCompleted message contract, as consumed by order-service. */
    private record PaymentCompleted(UUID orderId, UUID paymentId) {
    }

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    KafkaPaymentEvents(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Bean
    NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(TOPIC).build();
    }

    /** Waits for the broker to acknowledge, so a failed publish fails the surrounding transaction. */
    @Override
    public void completed(Payment payment) {
        try {
            String payload = json.writeValueAsString(new PaymentCompleted(payment.orderId(), payment.id()));
            kafka.send(TOPIC, payment.orderId().toString(), payload).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("could not publish PaymentCompleted for order " + payment.orderId(), e);
        }
    }
}
