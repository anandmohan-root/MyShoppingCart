package com.myshoppingcart.catalog;

import com.jayway.jsonpath.JsonPath;
import com.myshoppingcart.catalog.adapter.in.web.KeycloakRealmRoles;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product management against real PostgreSQL and Kafka. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired
    MockMvc mvc;

    @Test
    void anAdminAddsAProductThatIsAnnouncedAndListed() throws Exception {
        String id = JsonPath.read(asAdmin(post("/products"), "{\"name\":\"  Milk Frother \",\"price\":39.90}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Milk Frother"))
                .andExpect(jsonPath("$.onSale").value(true))
                .andReturn().getResponse().getContentAsString(), "$.id");

        String created = readFor("product-created", id).getFirst();
        assertThat(JsonPath.<String>read(created, "$.productId")).isEqualTo(id);
        mvc.perform(get("/products").with(jwt()))
                .andExpect(jsonPath("$[*].id", hasItem(id)));
    }

    @Test
    void anAdminChangesThePriceAndTakesTheProductOffSale() throws Exception {
        String id = JsonPath.read(asAdmin(post("/products"), "{\"name\":\"Tamper\",\"price\":20.00}")
                .andReturn().getResponse().getContentAsString(), "$.id");

        asAdmin(patch("/products/{id}", id), "{\"price\":24.50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(24.5))
                .andExpect(jsonPath("$.name").value("Tamper"));
        mvc.perform(get("/products/{id}", id).with(jwt())).andExpect(jsonPath("$.price").value(24.5));

        asAdmin(patch("/products/{id}", id), "{\"onSale\":false}")
                .andExpect(jsonPath("$.onSale").value(false));
        mvc.perform(get("/products/{id}", id).with(jwt())).andExpect(status().isNotFound());
        mvc.perform(get("/products").with(jwt())).andExpect(jsonPath("$[*].id", not(hasItem(id))));

        asAdmin(patch("/products/{id}", id), "{\"onSale\":true}");
        mvc.perform(get("/products/{id}", id).with(jwt())).andExpect(status().isOk());
    }

    @Test
    void badNamesPricesAndUnknownProductsAreRefused() throws Exception {
        asAdmin(post("/products"), "{\"name\":\" \",\"price\":10}").andExpect(status().isBadRequest());
        asAdmin(post("/products"), "{\"name\":\"Cup\",\"price\":0}").andExpect(status().isBadRequest());
        asAdmin(post("/products"), "{\"name\":\"Cup\",\"price\":1.999}").andExpect(status().isBadRequest());
        asAdmin(post("/products"), "{\"name\":\"Cup\"}").andExpect(status().isBadRequest());
        asAdmin(patch("/products/{id}", UUID.randomUUID()), "{\"price\":5}").andExpect(status().isNotFound());
    }

    @Test
    void theSeededProductsAreStillOnSale() throws Exception {
        mvc.perform(get("/products/{id}", "11111111-1111-1111-1111-111111111111").with(jwt()))
                .andExpect(jsonPath("$.name").value("Espresso Machine"))
                .andExpect(jsonPath("$.onSale").value(true));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String body) throws Exception {
        RequestPostProcessor admin = jwt()
                .jwt(j -> j.claim("realm_access", Map.of("roles", List.of("admin"))).claim("preferred_username", "test-admin"))
                .authorities(new KeycloakRealmRoles());
        return mvc.perform(request.with(admin).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static List<String> readFor(String topic, String key) {
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
            while (values.isEmpty() && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        values.add(record.value());
                    }
                }
            }
        }
        return values;
    }
}
