package com.myshoppingcart.inventory;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import com.myshoppingcart.inventory.adapter.in.web.KeycloakRealmRoles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inventory's part of the saga against real PostgreSQL and Kafka. The tests send the events
 * order-service and payment-service would send, and give every test its own product so the
 * stock levels do not interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InventoryFlowIntegrationTest {

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

    @Autowired
    MockMvc mvc;

    @Test
    void reservesStockOnceAndPassesTheOrderOnToPayment() {
        UUID product = productWithStock(10);
        UUID order = UUID.randomUUID();

        orderCreated(order, product, 3);
        orderCreated(order, product, 3);

        String reserved = readFor("stock-reserved", order, 1).getFirst();
        assertThat(JsonPath.<Double>read(reserved, "$.amount")).isEqualTo(30.0);
        assertThat(JsonPath.<String>read(reserved, "$.customerId")).isEqualTo("alice");
        assertThat(available(product)).isEqualTo(7);
    }

    @Test
    void rejectsAnOrderForMoreThanIsInStockAndReservesNothing() {
        UUID plenty = productWithStock(100);
        UUID scarce = productWithStock(2);
        UUID order = UUID.randomUUID();

        kafkaTemplate.send("order-created", order.toString(), """
                {"orderId":"%s","customerId":"alice","amount":50,
                 "lines":[{"productId":"%s","quantity":1},{"productId":"%s","quantity":3}]}"""
                .formatted(order, plenty, scarce));

        String rejected = readFor("stock-rejected", order, 1).getFirst();
        assertThat(JsonPath.<String>read(rejected, "$.reason")).contains(scarce.toString());
        assertThat(available(plenty)).isEqualTo(100);
        assertThat(available(scarce)).isEqualTo(2);
        assertThat(readFor("stock-reserved", order, 0)).isEmpty();
    }

    @Test
    void declinedPaymentPutsTheStockBackOnce() {
        UUID product = productWithStock(10);
        UUID order = UUID.randomUUID();
        orderCreated(order, product, 4);
        await().atMost(Duration.ofSeconds(20)).until(() -> available(product) == 6);

        kafkaTemplate.send("payment-declined", order.toString(), "{\"orderId\":\"%s\"}".formatted(order));
        kafkaTemplate.send("payment-declined", order.toString(), "{\"orderId\":\"%s\"}".formatted(order));

        await().atMost(Duration.ofSeconds(20)).until(() -> available(product) == 10);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> available(product) == 10);
    }

    @Test
    void cancellationArrivingBeforeTheOrderMeansNothingIsReserved() {
        UUID product = productWithStock(10);
        UUID order = UUID.randomUUID();

        kafkaTemplate.send("order-cancelled", order.toString(), "{\"orderId\":\"%s\"}".formatted(order));
        await().atMost(Duration.ofSeconds(20)).until(() -> reservationStatus(order) != null);
        orderCreated(order, product, 4);

        assertThat(readFor("stock-reserved", order, 0)).isEmpty();
        assertThat(available(product)).isEqualTo(10);
        assertThat(reservationStatus(order)).isEqualTo("RELEASED");
    }

    @Test
    void stockCanBeReadWithAToken() throws Exception {
        UUID product = productWithStock(5);

        mvc.perform(get("/stock/{id}", product).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(5));
        mvc.perform(get("/stock").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.productId == '%s')].available".formatted(product)).value(5));
        mvc.perform(get("/stock"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminRestocksAndGetsTheNewLevel() throws Exception {
        UUID product = productWithStock(5);

        restock(product, "10", "admin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(15));

        assertThat(available(product)).isEqualTo(15);
    }

    @Test
    void aCustomerCannotRestock() throws Exception {
        UUID product = productWithStock(5);

        restock(product, "10", "default-roles-shop").andExpect(status().isForbidden());

        assertThat(available(product)).isEqualTo(5);
    }

    @Test
    void restockRejectsBadQuantitiesAndUnknownProducts() throws Exception {
        UUID product = productWithStock(5);

        restock(product, "0", "admin").andExpect(status().isBadRequest());
        restock(product, "10001", "admin").andExpect(status().isBadRequest());
        restock(UUID.randomUUID(), "10", "admin").andExpect(status().isNotFound());
        assertThat(available(product)).isEqualTo(5);
    }

    @Test
    void aNewProductStartsWithNoStockAndKeepsItsStockIfAnnouncedAgain() throws Exception {
        UUID product = UUID.randomUUID();
        String created = "{\"productId\":\"%s\",\"name\":\"Milk Frother\",\"price\":39.90}".formatted(product);

        kafkaTemplate.send("product-created", product.toString(), created);
        await().atMost(Duration.ofSeconds(20)).until(() -> stockRow(product) != null);
        assertThat(available(product)).isZero();

        restock(product, "7", "admin").andExpect(status().isOk());
        kafkaTemplate.send("product-created", product.toString(), created);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> available(product) == 7);
    }

    @Test
    void unreadableMessageMovesToTheDeadLetterTopic() {
        UUID key = UUID.randomUUID();
        kafkaTemplate.send("order-created", key.toString(), "this is not JSON");

        assertThat(readFor("order-created-dlt", key, 1)).containsExactly("this is not JSON");
    }

    /** Restocks with a token holding the given Keycloak realm role, read the way the service reads it. */
    private ResultActions restock(UUID product, String quantity, String realmRole) throws Exception {
        return mvc.perform(post("/stock/{id}/restock", product)
                .with(jwt()
                        .jwt(j -> j.claim("realm_access", Map.of("roles", List.of(realmRole)))
                                .claim("preferred_username", "test-" + realmRole))
                        .authorities(new KeycloakRealmRoles()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + "}"));
    }

    private UUID productWithStock(int available) {
        UUID product = UUID.randomUUID();
        jdbc.sql("INSERT INTO stock (product_id, available) VALUES (:id, :available)")
                .param("id", product)
                .param("available", available)
                .update();
        return product;
    }

    private void orderCreated(UUID order, UUID product, int quantity) {
        kafkaTemplate.send("order-created", order.toString(), """
                {"orderId":"%s","customerId":"alice","amount":%d.00,"lines":[{"productId":"%s","quantity":%d}]}"""
                .formatted(order, quantity * 10, product, quantity));
    }

    private Integer stockRow(UUID product) {
        return jdbc.sql("SELECT available FROM stock WHERE product_id = :id").param("id", product)
                .query(Integer.class).optional().orElse(null);
    }

    private int available(UUID product) {
        return jdbc.sql("SELECT available FROM stock WHERE product_id = :id").param("id", product)
                .query(Integer.class).single();
    }

    private String reservationStatus(UUID order) {
        return jdbc.sql("SELECT status FROM reservation WHERE order_id = :id").param("id", order)
                .query(String.class).optional().orElse(null);
    }

    /**
     * Reads the messages keyed by this order until the expected number arrived, then listens a
     * little longer to catch an unwanted extra one. With {@code expected} 0 it listens for 5 s.
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
            long deadline = System.currentTimeMillis() + (expected == 0 ? 5_000 : 30_000);
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.toString().equals(record.key())) {
                        values.add(record.value());
                    }
                }
                if (expected > 0 && values.size() >= expected) {
                    deadline = Math.min(deadline, System.currentTimeMillis() + 2_000);
                }
            }
        }
        return values;
    }
}
