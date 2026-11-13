package com.myshoppingcart.notification.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.notification.application.NotificationService;
import com.myshoppingcart.notification.domain.Notification;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Listens to events the other services already publish; none of them knows this service exists.
 * Each event that matters to the customer becomes one email.
 */
@Component
class OrderEventsListener {

    /** The part of OrderCreated (published by order-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderCreated(UUID orderId, String customerEmail, BigDecimal amount) {
    }

    /** Every other event only needs the order id. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AboutAnOrder(UUID orderId) {
    }

    private final NotificationService notifications;
    private final ObjectMapper json;

    OrderEventsListener(NotificationService notifications, ObjectMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @KafkaListener(topics = "order-created")
    void onOrderCreated(String payload) throws JsonProcessingException {
        OrderCreated event = json.readValue(payload, OrderCreated.class);
        notifications.orderCreated(event.orderId(), event.customerEmail(), event.amount());
    }

    @KafkaListener(topics = "payment-completed")
    void onPaymentCompleted(String payload) throws JsonProcessingException {
        notifications.notify(orderId(payload), Notification.CONFIRMED);
    }

    @KafkaListener(topics = "payment-declined")
    void onPaymentDeclined(String payload) throws JsonProcessingException {
        notifications.notify(orderId(payload), Notification.PAYMENT_DECLINED);
    }

    @KafkaListener(topics = "stock-rejected")
    void onStockRejected(String payload) throws JsonProcessingException {
        notifications.notify(orderId(payload), Notification.OUT_OF_STOCK);
    }

    @KafkaListener(topics = "order-cancelled")
    void onOrderCancelled(String payload) throws JsonProcessingException {
        notifications.notify(orderId(payload), Notification.CANCELLED);
    }

    @KafkaListener(topics = "refund-requested")
    void onRefundRequested(String payload) throws JsonProcessingException {
        notifications.notify(orderId(payload), Notification.REFUND_STARTED);
    }

    private UUID orderId(String payload) throws JsonProcessingException {
        return json.readValue(payload, AboutAnOrder.class).orderId();
    }
}
