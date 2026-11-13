package com.myshoppingcart.inventory.adapter.in.web;

import com.myshoppingcart.inventory.application.InventoryService;
import com.myshoppingcart.inventory.domain.StockItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** How many of each product can still be ordered, and restocking (admins only, see SecurityConfig). */
@RestController
class StockController {

    record RestockRequest(int quantity) {
    }

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

    /** Adds {@code quantity} to the product's stock and returns the new level; 404 for a product without stock. */
    @PostMapping("/stock/{productId}/restock")
    ResponseEntity<StockItem> restock(@PathVariable UUID productId, @RequestBody RestockRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(inventory.restock(productId, request.quantity(), jwt.getClaimAsString("preferred_username")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
