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

/**
 * Payment side of the order-to-payment flow against real PostgreSQL and Kafka:
 * a duplicated OrderCreated (at-least-once delivery) produces exactly one payment
 * and exactly one PaymentCompleted.
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
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        send(first);
        send(first);
        // The topic has one partition, so once the second order is answered both copies of the first were handled.
        send(second);

        List<String> paidOrderIds = readPaymentCompleted(2);

        assertThat(paidOrderIds).containsExactly(first.toString(), second.toString());
        assertThat(jdbc.sql("SELECT count(*) FROM payment").query(Long.class).single()).isEqualTo(2);
    }

    private void send(UUID orderId) {
        kafkaTemplate.send("order-created", orderId.toString(),
                "{\"orderId\":\"%s\",\"customerId\":\"alice\",\"amount\":37.50}".formatted(orderId));
    }

    /** Reads until the expected number of messages arrived, then listens a little longer to catch an unwanted extra. */
    private static List<String> readPaymentCompleted(int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> orderIds = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of("payment-completed"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    orderIds.add(JsonPath.read(record.value(), "$.orderId"));
                }
                if (orderIds.size() >= expected) {
                    deadline = Math.min(deadline, System.currentTimeMillis() + 2_000);
                }
            }
        }
        return orderIds;
    }
}
