package com.myshoppingcart.order;

import com.myshoppingcart.order.application.Carts;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
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
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cart, orders and the order side of the order-to-payment saga against real PostgreSQL, Kafka
 * and Redis. The tests answer
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

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID OTHER_PRODUCT = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    ProductCatalog catalog;

    @MockitoSpyBean
    Carts carts;

    @BeforeEach
    void priceTheProduct() {
        when(catalog.priceOf(PRODUCT)).thenReturn(Optional.of(new BigDecimal("18.75")));
        when(catalog.priceOf(OTHER_PRODUCT)).thenReturn(Optional.of(new BigDecimal("249.00")));
    }

    @Test
    void orderBecomesPaidAfterPaymentCompleted() throws Exception {
        String response = mvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(PRODUCT, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(18.75))
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
    void customersSeeOnlyTheirOwnOrdersNewestFirst() throws Exception {
        String first = placeAs("bob");
        String second = placeAs("bob");
        placeAs("carol");

        mvc.perform(get("/orders").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[0].createdAt").exists());

        mvc.perform(get("/orders").param("limit", "1").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(second));

        mvc.perform(get("/orders").with(jwt().jwt(j -> j.subject("dave"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void checkoutTurnsTheCartIntoOneOrderAndEmptiesIt() throws Exception {
        setInCart("erin", PRODUCT, 1).andExpect(status().isOk());
        setInCart("erin", OTHER_PRODUCT, 1).andExpect(status().isOk());
        // Setting the quantity again replaces it rather than adding a second line.
        setInCart("erin", PRODUCT, 2).andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(get("/cart").with(jwt().jwt(j -> j.subject("frank"))))
                .andExpect(jsonPath("$.items.length()").value(0));

        String response = mvc.perform(post("/cart/checkout").with(jwt().jwt(j -> j.subject("erin"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.total").value(286.5))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(response, "$.id");

        String orderCreated = readFor("order-created", orderId, 1).getFirst();
        assertThat(JsonPath.<Double>read(orderCreated, "$.amount")).isEqualTo(286.5);
        mvc.perform(get("/orders/{id}", orderId).with(jwt().jwt(j -> j.subject("erin"))))
                .andExpect(jsonPath("$.lines.length()").value(2));
        mvc.perform(get("/cart").with(jwt().jwt(j -> j.subject("erin"))))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(post("/cart/checkout").with(jwt().jwt(j -> j.subject("erin"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cartThatCouldNotBeEmptiedIsNotOrderedTwice() throws Exception {
        doThrow(new RedisConnectionFailureException("Redis is down"))
                .doCallRealMethod()
                .when(carts).clear("hank");
        setInCart("hank", PRODUCT, 1).andExpect(status().isOk());

        // The order is placed although the cart could not be emptied afterwards.
        String first = JsonPath.read(mvc.perform(post("/cart/checkout").with(jwt().jwt(j -> j.subject("hank"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        // Checking out again returns the same order and empties the cart.
        mvc.perform(post("/cart/checkout").with(jwt().jwt(j -> j.subject("hank"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first));
        mvc.perform(get("/cart").with(jwt().jwt(j -> j.subject("hank"))))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/orders").with(jwt().jwt(j -> j.subject("hank"))))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(readFor("order-created", first, 1)).hasSize(1);

        // The next product starts a new cart, which becomes a new order.
        setInCart("hank", PRODUCT, 3).andExpect(jsonPath("$.items[0].quantity").value(3));
        mvc.perform(post("/cart/checkout").with(jwt().jwt(j -> j.subject("hank"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(first)));
    }

    @Test
    void anOrderedCartLeftBehindIsEmptiedWhenTheCustomerComesBack() throws Exception {
        doThrow(new RedisConnectionFailureException("Redis is down"))
                .doCallRealMethod()
                .when(carts).clear("ivy");
        setInCart("ivy", PRODUCT, 1).andExpect(status().isOk());
        mvc.perform(post("/cart/checkout").with(jwt().jwt(j -> j.subject("ivy"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/cart").with(jwt().jwt(j -> j.subject("ivy"))))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void cartRejectsUnknownProductsAndBadQuantities() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(catalog.priceOf(unknown)).thenReturn(Optional.empty());

        setInCart("gina", unknown, 1).andExpect(status().isBadRequest());
        setInCart("gina", PRODUCT, 0).andExpect(status().isBadRequest());

        setInCart("gina", PRODUCT, 1).andExpect(status().isOk());
        mvc.perform(delete("/cart/items/{id}", PRODUCT).with(jwt().jwt(j -> j.subject("gina"))))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void unreadableMessageMovesToTheDeadLetterTopic() {
        String key = UUID.randomUUID().toString();
        kafkaTemplate.send("payment-completed", key, "this is not JSON");

        assertThat(readFor("payment-completed-dlt", key, 1)).containsExactly("this is not JSON");
    }

    private String place() throws Exception {
        return placeAs("alice");
    }

    private String placeAs(String customer) throws Exception {
        String response = mvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(PRODUCT, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions setInCart(String customer, UUID productId, int quantity) throws Exception {
        return mvc.perform(put("/cart/items/{id}", productId)
                .with(jwt().jwt(j -> j.subject(customer)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":%d}".formatted(quantity)));
    }

    private static String itemsJson(UUID productId, int quantity) {
        return "{\"items\":[{\"productId\":\"%s\",\"quantity\":%d}]}".formatted(productId, quantity);
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
