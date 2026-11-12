package com.myshoppingcart.order.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.order.application.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class StockRejectedListener {

    /** The part of the StockRejected message (published by inventory-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StockRejected(UUID orderId) {
    }

    private final OrderService orders;
    private final ObjectMapper json;

    StockRejectedListener(OrderService orders, ObjectMapper json) {
        this.orders = orders;
        this.json = json;
    }

    @KafkaListener(topics = "stock-rejected")
    void onStockRejected(String payload) throws JsonProcessingException {
        orders.outOfStock(json.readValue(payload, StockRejected.class).orderId());
    }
}
