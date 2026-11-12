package com.myshoppingcart.order.adapter.in.messaging;

import com.myshoppingcart.order.application.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PaymentCompletedListener {

    /** The PaymentCompleted message contract, as published by payment-service. */
    private record PaymentCompleted(UUID orderId) {
    }

    private final OrderService orders;
    private final ObjectMapper json;

    PaymentCompletedListener(OrderService orders, ObjectMapper json) {
        this.orders = orders;
        this.json = json;
    }

    @KafkaListener(topics = "payment-completed")
    void onPaymentCompleted(String payload) throws JsonProcessingException {
        orders.paymentCompleted(json.readValue(payload, PaymentCompleted.class).orderId());
    }
}
