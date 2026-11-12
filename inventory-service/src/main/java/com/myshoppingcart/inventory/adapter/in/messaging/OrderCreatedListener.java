package com.myshoppingcart.inventory.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.inventory.application.InventoryService;
import com.myshoppingcart.inventory.domain.OrderLine;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
class OrderCreatedListener {

    /** The part of the OrderCreated message (published by order-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderCreated(UUID orderId, String customerId, BigDecimal amount, List<OrderLine> lines) {
    }

    private final InventoryService inventory;
    private final ObjectMapper json;

    OrderCreatedListener(InventoryService inventory, ObjectMapper json) {
        this.inventory = inventory;
        this.json = json;
    }

    @KafkaListener(topics = "order-created")
    void onOrderCreated(String payload) throws JsonProcessingException {
        OrderCreated event = json.readValue(payload, OrderCreated.class);
        inventory.reserve(event.orderId(), event.customerId(), event.amount(), event.lines() == null ? List.of() : event.lines());
    }
}
