package com.myshoppingcart.inventory.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.inventory.application.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** A product added to the catalog gets a stock entry at 0, ready for an admin to restock. */
@Component
class ProductCreatedListener {

    /** The part of the ProductCreated message (published by catalog-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductCreated(UUID productId) {
    }

    private final InventoryService inventory;
    private final ObjectMapper json;

    ProductCreatedListener(InventoryService inventory, ObjectMapper json) {
        this.inventory = inventory;
        this.json = json;
    }

    @KafkaListener(topics = "product-created")
    void onProductCreated(String payload) throws JsonProcessingException {
        inventory.open(json.readValue(payload, ProductCreated.class).productId());
    }
}
