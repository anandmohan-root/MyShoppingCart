package com.myshoppingcart.payment.adapter.in.messaging;

import com.myshoppingcart.payment.application.PaymentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** An order is charged once inventory has reserved its stock. */
@Component
class StockReservedListener {

    /** The part of the StockReserved message (published by inventory-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StockReserved(UUID orderId, BigDecimal amount) {
    }

    private final PaymentService payments;
    private final ObjectMapper json;

    StockReservedListener(PaymentService payments, ObjectMapper json) {
        this.payments = payments;
        this.json = json;
    }

    @KafkaListener(topics = "stock-reserved")
    void onStockReserved(String payload) throws JsonProcessingException {
        StockReserved event = json.readValue(payload, StockReserved.class);
        payments.payFor(event.orderId(), event.amount());
    }
}
