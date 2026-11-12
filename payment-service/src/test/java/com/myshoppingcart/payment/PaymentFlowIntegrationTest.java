package com.myshoppingcart.payment;

import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Payment side of the order-to-payment flow against real PostgreSQL and Kafka. Events leave
 * through the outbox, so these tests also cover the relay.
 */
@SpringBootTest
@Testcontainers
class PaymentFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcClient jdbc;

    @Test
    void duplicateOrderCreatedIsPaidOnce() {
        UUID orderId = UUID.randomUUID();
        orderCreated(orderId, "37.50");
        orderCreated(orderId, "37.50");

        List<String> events = readFor("payment-completed", orderId, 1);

        assertThat(events).hasSize(1);
        assertThat(paymentStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT count(*) FROM payment WHERE order_id = :id").param("id", orderId)
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void amountAboveTheLimitIsDeclined() {
        UUID orderId = UUID.randomUUID();
        orderCreated(orderId, "1245.00");

        List<String> events = readFor("payment-declined", orderId, 1);

        assertThat(events).hasSize(1);
        assertThat(JsonPath.<String>read(events.getFirst(), "$.reason")).isEqualTo("amount above the card limit");
        assertThat(paymentStatus(orderId)).isEqualTo("DECLINED");
    }

    @Test
    void refundRequestedRefundsACompletedPayment() {
        UUID orderId = UUID.randomUUID();
        orderCreated(orderId, "37.50");
        await().atMost(Duration.ofSeconds(20)).until(() -> "COMPLETED".equals(paymentStatus(orderId)));

        kafkaTemplate.send("refund-requested", orderId.toString(), "{\"orderId\":\"%s\",\"amount\":37.50}".formatted(orderId));

        await().atMost(Duration.ofSeconds(20)).until(() -> "REFUNDED".equals(paymentStatus(orderId)));
    }

    @Test
    void unreadableMessageMovesToTheDeadLetterTopic() {
        UUID key = UUID.randomUUID();
        kafkaTemplate.send("order-created", key.toString(), "this is not JSON");

        assertThat(readFor("order-created-dlt", key, 1)).containsExactly("this is not JSON");
    }

    private void orderCreated(UUID orderId, String amount) {
        kafkaTemplate.send("order-created", orderId.toString(),
                "{\"orderId\":\"%s\",\"customerId\":\"alice\",\"amount\":%s}".formatted(orderId, amount));
    }

    private String paymentStatus(UUID orderId) {
        return jdbc.sql("SELECT status FROM payment WHERE order_id = :id").param("id", orderId)
                .query(String.class).optional().orElse(null);
    }

    /**
     * Reads the messages keyed by this order until the expected number arrived, then listens a
     * little longer to catch an unwanted extra one.
     */
    private static List<String> readFor(String topic, UUID key, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.toString().equals(record.key())) {
                        values.add(record.value());
                    }
                }
                if (values.size() >= expected) {
                    deadline = Math.min(deadline, System.currentTimeMillis() + 2_000);
                }
            }
        }
        return values;
    }
}
