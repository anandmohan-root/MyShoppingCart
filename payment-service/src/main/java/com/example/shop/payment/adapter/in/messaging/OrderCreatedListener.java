package com.example.shop.payment.adapter.in.messaging;

import com.example.shop.payment.application.PaymentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
class OrderCreatedListener {

    /** The part of the OrderCreated message (published by order-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderCreated(UUID orderId, BigDecimal amount) {
    }

    private final PaymentService payments;
    private final ObjectMapper json;

    OrderCreatedListener(PaymentService payments, ObjectMapper json) {
        this.payments = payments;
        this.json = json;
    }

    @KafkaListener(topics = "order-created")
    void onOrderCreated(String payload) throws JsonProcessingException {
        OrderCreated event = json.readValue(payload, OrderCreated.class);
        payments.payFor(event.orderId(), event.amount());
    }
}
