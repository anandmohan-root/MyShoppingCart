package com.myshoppingcart.order;

import com.myshoppingcart.order.application.ProductCatalog;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
 * Order side of the order-to-payment flow against real PostgreSQL and Kafka:
 * place an order, see OrderCreated leave through the outbox, then answer with
 * PaymentCompleted (standing in for payment-service) and see the order become PAID.
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

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    ProductCatalog catalog;

    @Test
    void orderBecomesPaidAfterPaymentCompleted() throws Exception {
        UUID productId = UUID.randomUUID();
        when(catalog.priceOf(productId)).thenReturn(Optional.of(new BigDecimal("18.75")));

        String response = mvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":2}".formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(37.5))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(response, "$.id");

        String orderCreated = readOne("order-created");
        assertThat(JsonPath.<String>read(orderCreated, "$.orderId")).isEqualTo(orderId);
        assertThat(JsonPath.<Double>read(orderCreated, "$.amount")).isEqualTo(37.5);

        kafkaTemplate.send("payment-completed", orderId, "{\"orderId\":\"%s\"}".formatted(orderId));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/orders/{id}", orderId).with(jwt().jwt(j -> j.subject("alice"))))
                        .andExpect(jsonPath("$.status").value("PAID")));

        mvc.perform(get("/orders/{id}", orderId).with(jwt().jwt(j -> j.subject("mallory"))))
                .andExpect(status().isNotFound());
    }

    private static String readOne(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    return record.value();
                }
            }
        }
        throw new AssertionError("no message on " + topic);
    }
}
