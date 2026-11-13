package com.myshoppingcart.notification;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Notifications against real PostgreSQL, Kafka and a Mailpit mail server, whose API shows what
 * was sent. The tests send the events the other services would send.
 */
@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@Testcontainers
class NotificationFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.27").withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void aPaidOrderGetsOneConfirmationEvenIfThePaymentEventRepeats() {
        String to = mailbox();
        UUID order = orderCreated(to, "108.25");

        send("payment-completed", order);
        send("payment-completed", order);

        List<String> subjects = awaitSubjects(to, 1);
        assertThat(subjects).containsExactly("Your order is confirmed");
        assertThat(bodyOfFirst(to)).contains(order.toString().substring(0, 8)).contains("108.25");
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).until(() -> subjects(to).size() == 1);
    }

    @Test
    void eachStepOfACancelledPaidOrderGetsItsOwnEmail() {
        String to = mailbox();
        UUID order = orderCreated(to, "37.50");

        send("payment-completed", order);
        send("order-cancelled", order);
        send("refund-requested", order);

        assertThat(awaitSubjects(to, 3)).containsExactlyInAnyOrder(
                "Your order is confirmed", "Your order is cancelled", "Your refund is on its way");
    }

    @Test
    void declinedAndOutOfStockOrdersAreExplained() {
        String declined = mailbox();
        String noStock = mailbox();

        send("payment-declined", orderCreated(declined, "1245.00"));
        send("stock-rejected", orderCreated(noStock, "18.75"));

        assertThat(awaitSubjects(declined, 1)).containsExactly("Your payment was declined");
        assertThat(awaitSubjects(noStock, 1)).containsExactly("Your order could not be filled");
    }

    @Test
    void anEventArrivingBeforeItsOrderIsSentOnceTheOrderIsKnown() {
        String to = mailbox();
        UUID order = UUID.randomUUID();

        send("payment-completed", order);
        kafkaTemplate.send("order-created", order.toString(), orderCreatedJson(order, to, "20.00"));

        assertThat(awaitSubjects(to, 1)).containsExactly("Your order is confirmed");
    }

    @Test
    void anOrderWithoutAnEmailAddressSendsNothing() {
        UUID order = UUID.randomUUID();
        kafkaTemplate.send("order-created", order.toString(),
                "{\"orderId\":\"%s\",\"customerEmail\":null,\"amount\":10.00}".formatted(order));
        String witness = mailbox();
        UUID witnessOrder = orderCreated(witness, "1.00");

        send("payment-completed", order);
        send("payment-completed", witnessOrder);

        // The witness order's email proves the events were processed; the other produced none.
        awaitSubjects(witness, 1);
        assertThat(allRecipients()).doesNotContain("null");
    }

    // ---------- helpers

    /** A fresh address per test, so tests do not see each other's emails. */
    private static String mailbox() {
        return "customer-" + UUID.randomUUID() + "@example.com";
    }

    private UUID orderCreated(String email, String amount) {
        UUID order = UUID.randomUUID();
        kafkaTemplate.send("order-created", order.toString(), orderCreatedJson(order, email, amount));
        return order;
    }

    private static String orderCreatedJson(UUID order, String email, String amount) {
        return "{\"orderId\":\"%s\",\"customerId\":\"c\",\"customerEmail\":\"%s\",\"amount\":%s,\"lines\":[]}"
                .formatted(order, email, amount);
    }

    private void send(String topic, UUID order) {
        kafkaTemplate.send(topic, order.toString(), "{\"orderId\":\"%s\"}".formatted(order));
    }

    private static RestClient mailpitApi() {
        return RestClient.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
    }

    private static List<String> subjects(String to) {
        String result = mailpitApi().get().uri("/api/v1/search?query={q}", "to:" + to).retrieve().body(String.class);
        return JsonPath.read(result, "$.messages[*].Subject");
    }

    private static List<String> awaitSubjects(String to, int count) {
        await().atMost(Duration.ofSeconds(30)).until(() -> subjects(to).size() >= count);
        return subjects(to);
    }

    private static String bodyOfFirst(String to) {
        String result = mailpitApi().get().uri("/api/v1/search?query={q}", "to:" + to).retrieve().body(String.class);
        String id = JsonPath.read(result, "$.messages[0].ID");
        return JsonPath.read(mailpitApi().get().uri("/api/v1/message/{id}", id).retrieve().body(String.class), "$.Text");
    }

    private static List<String> allRecipients() {
        String result = mailpitApi().get().uri("/api/v1/messages?limit=500").retrieve().body(String.class);
        return JsonPath.read(result, "$.messages[*].To[*].Address");
    }
}
