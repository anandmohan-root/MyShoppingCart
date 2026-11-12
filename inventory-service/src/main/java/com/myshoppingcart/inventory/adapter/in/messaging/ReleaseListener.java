package com.myshoppingcart.inventory.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.inventory.application.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** A declined payment (from payment-service) or a cancelled order (from order-service) frees its stock. */
@Component
class ReleaseListener {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AboutAnOrder(UUID orderId) {
    }

    private final InventoryService inventory;
    private final ObjectMapper json;

    ReleaseListener(InventoryService inventory, ObjectMapper json) {
        this.inventory = inventory;
        this.json = json;
    }

    @KafkaListener(topics = {"payment-declined", "order-cancelled"})
    void onReleaseNeeded(String payload) throws JsonProcessingException {
        inventory.release(json.readValue(payload, AboutAnOrder.class).orderId());
    }
}
