package com.myshoppingcart.inventory.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.inventory.application.InventoryEvents;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Transactional outbox: events are written to the outbox table in the same transaction as the
 * reservation, and {@link OutboxRelay} publishes them to Kafka afterwards.
 */
@Component
class OutboxInventoryEvents implements InventoryEvents {

    static final String STOCK_RESERVED_TOPIC = "stock-reserved";
    static final String STOCK_REJECTED_TOPIC = "stock-rejected";

    /** The StockReserved message contract, as consumed by payment-service. */
    private record StockReserved(UUID orderId, String customerId, BigDecimal amount) {
    }

    /** The StockRejected message contract, as consumed by order-service. */
    private record StockRejected(UUID orderId, String reason) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    OutboxInventoryEvents(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void reserved(UUID orderId, String customerId, BigDecimal amount) {
        record(STOCK_RESERVED_TOPIC, orderId, new StockReserved(orderId, customerId, amount));
    }

    @Override
    public void rejected(UUID orderId, String reason) {
        record(STOCK_REJECTED_TOPIC, orderId, new StockRejected(orderId, reason));
    }

    private void record(String topic, UUID orderId, Object event) {
        try {
            jdbc.sql("INSERT INTO outbox (topic, message_key, payload) VALUES (:topic, :key, :payload)")
                    .param("topic", topic)
                    .param("key", orderId.toString())
                    .param("payload", json.writeValueAsString(event))
                    .update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
