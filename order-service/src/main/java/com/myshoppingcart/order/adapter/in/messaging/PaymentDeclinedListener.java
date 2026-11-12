package com.myshoppingcart.order.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.order.application.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PaymentDeclinedListener {

    /** The part of the PaymentDeclined message (published by payment-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PaymentDeclined(UUID orderId) {
    }

    private final OrderService orders;
    private final ObjectMapper json;

    PaymentDeclinedListener(OrderService orders, ObjectMapper json) {
        this.orders = orders;
        this.json = json;
    }

    @KafkaListener(topics = "payment-declined")
    void onPaymentDeclined(String payload) throws JsonProcessingException {
        orders.paymentDeclined(json.readValue(payload, PaymentDeclined.class).orderId());
    }
}
