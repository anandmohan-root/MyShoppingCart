package com.myshoppingcart.order.adapter.in.web;

import com.myshoppingcart.order.application.CartService;
import com.myshoppingcart.order.application.OrderItem;
import com.myshoppingcart.order.domain.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The caller's own cart. Errors are mapped to HTTP statuses in {@link ApiErrors}. */
@RestController
class CartController {

    record Cart(List<OrderItem> items) {
    }

    record QuantityRequest(int quantity) {
    }

    private final CartService carts;

    CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping("/cart")
    Cart view(@AuthenticationPrincipal Jwt jwt) {
        return new Cart(carts.view(jwt.getSubject()));
    }

    /** Adds the product, or changes its quantity if it is already in the cart. */
    @PutMapping("/cart/items/{productId}")
    Cart put(@PathVariable UUID productId, @RequestBody QuantityRequest request, @AuthenticationPrincipal Jwt jwt) {
        return new Cart(carts.put(jwt.getSubject(), productId, request.quantity()));
    }

    @DeleteMapping("/cart/items/{productId}")
    Cart remove(@PathVariable UUID productId, @AuthenticationPrincipal Jwt jwt) {
        return new Cart(carts.remove(jwt.getSubject(), productId));
    }

    @DeleteMapping("/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@AuthenticationPrincipal Jwt jwt) {
        carts.clear(jwt.getSubject());
    }

    /** Places one order for everything in the cart and empties it. An empty cart returns 400. */
    @PostMapping("/cart/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    Order checkout(@AuthenticationPrincipal Jwt jwt) {
        return carts.checkout(jwt.getSubject());
    }
}
