package com.myshoppingcart.payment.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.payment.application.PaymentEvents;
import com.myshoppingcart.payment.domain.Payment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Transactional outbox: events are written to the outbox table in the same transaction as the
 * payment, and {@link OutboxRelay} publishes them to Kafka afterwards. Kafka being down no longer
 * fails a payment, and a payment can no longer be announced without being stored.
 */
@Component
class OutboxPaymentEvents implements PaymentEvents {

    static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";
    static final String PAYMENT_DECLINED_TOPIC = "payment-declined";

    /** The PaymentCompleted message contract, as consumed by order-service. */
    private record PaymentCompleted(UUID orderId, UUID paymentId) {
    }

    /** The PaymentDeclined message contract, as consumed by order-service. */
    private record PaymentDeclined(UUID orderId, UUID paymentId, String reason) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    OutboxPaymentEvents(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void completed(Payment payment) {
        record(PAYMENT_COMPLETED_TOPIC, payment, new PaymentCompleted(payment.orderId(), payment.id()));
    }

    @Override
    public void declined(Payment payment) {
        record(PAYMENT_DECLINED_TOPIC, payment, new PaymentDeclined(payment.orderId(), payment.id(), "amount above the card limit"));
    }

    private void record(String topic, Payment payment, Object event) {
        try {
            jdbc.sql("INSERT INTO outbox (topic, message_key, payload) VALUES (:topic, :key, :payload)")
                    .param("topic", topic)
                    .param("key", payment.orderId().toString())
                    .param("payload", json.writeValueAsString(event))
                    .update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
