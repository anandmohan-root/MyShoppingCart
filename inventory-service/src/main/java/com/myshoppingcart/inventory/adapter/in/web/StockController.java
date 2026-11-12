package com.myshoppingcart.inventory.adapter.in.web;

import com.myshoppingcart.inventory.application.InventoryService;
import com.myshoppingcart.inventory.domain.StockItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** How many of each product can still be ordered. */
@RestController
class StockController {

    private final InventoryService inventory;

    StockController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/stock")
    List<StockItem> all() {
        return inventory.all();
    }

    @GetMapping("/stock/{productId}")
    ResponseEntity<StockItem> one(@PathVariable UUID productId) {
        return ResponseEntity.of(inventory.find(productId));
    }
}
