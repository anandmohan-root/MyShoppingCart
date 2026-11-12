package com.myshoppingcart.order;

import com.myshoppingcart.order.application.ProductCatalog;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order side of the order-to-payment saga against real PostgreSQL and Kafka. The tests answer
 * with payment events themselves, standing in for payment-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    private static final UUID PRODUCT = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    ProductCatalog catalog;

    @BeforeEach
    void priceTheProduct() {
        when(catalog.priceOf(PRODUCT)).thenReturn(Optional.of(new BigDecimal("18.75")));
    }

    @Test
    void orderBecomesPaidAfterPaymentCompleted() throws Exception {
        String response = mvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":2}".formatted(PRODUCT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(37.5))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(response, "$.id");

        String orderCreated = readFor("order-created", orderId, 1).getFirst();
        assertThat(JsonPath.<Double>read(orderCreated, "$.amount")).isEqualTo(37.5);

        send("payment-completed", orderId);
        awaitStatus(orderId, "PAID");

        mvc.perform(get("/orders/{id}", orderId).with(jwt().jwt(j -> j.subject("mallory"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void declinedPaymentEndsTheOrderAndItCannotBeCancelled() throws Exception {
        String orderId = place();

        send("payment-declined", orderId);
        awaitStatus(orderId, "PAYMENT_DECLINED");

        mvc.perform(post("/orders/{id}/cancel", orderId).with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingAPaidOrderRequestsARefund() throws Exception {
        String orderId = place();
        send("payment-completed", orderId);
        awaitStatus(orderId, "PAID");

        mvc.perform(post("/orders/{id}/cancel", orderId).with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(readFor("refund-requested", orderId, 1)).hasSize(1);
    }

    @Test
    void paymentArrivingAfterCancellationIsRefundedNotPaid() throws Exception {
        String orderId = place();
        mvc.perform(post("/orders/{id}/cancel", orderId).with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isOk());

        send("payment-completed", orderId);

        assertThat(readFor("refund-requested", orderId, 1)).hasSize(1);
        mvc.perform(get("/orders/{id}", orderId).with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void onlyTheOwnerCanCancel() throws Exception {
        String orderId = place();

        mvc.perform(post("/orders/{id}/cancel", orderId).with(jwt().jwt(j -> j.subject("mallory"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unreadableMessageMovesToTheDeadLetterTopic() {
        String key = UUID.randomUUID().toString();
        kafkaTemplate.send("payment-completed", key, "this is not JSON");

        assertThat(readFor("payment-completed-dlt", key, 1)).containsExactly("this is not JSON");
    }

    private String place() throws Exception {
        String response = mvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(PRODUCT)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void send(String topic, String orderId) {
        kafkaTemplate.send(topic, orderId, "{\"orderId\":\"%s\"}".formatted(orderId));
    }

    private void awaitStatus(String orderId, String expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/orders/{id}", orderId).with(jwt().jwt(j -> j.subject("alice"))))
                        .andExpect(jsonPath("$.status").value(expected)));
    }

    /**
     * Reads the messages keyed by this order until the expected number arrived, then listens a
     * little longer to catch an unwanted extra one.
     */
    private static List<String> readFor(String topic, String key, int expected) {
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
                    if (key.equals(record.key())) {
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
