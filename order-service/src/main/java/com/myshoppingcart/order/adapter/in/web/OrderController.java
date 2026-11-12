package com.myshoppingcart.order.adapter.in.web;

import com.myshoppingcart.order.application.OrderItem;
import com.myshoppingcart.order.application.OrderService;
import com.myshoppingcart.order.domain.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The caller's own orders. Errors are mapped to HTTP statuses in {@link ApiErrors}. */
@RestController
class OrderController {

    record PlaceOrderRequest(List<OrderItem> items) {
    }

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    Order place(@RequestBody PlaceOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return orders.place(jwt.getSubject(), request.items() == null ? List.of() : request.items());
    }

    /** The caller's own orders, newest first; at most {@code limit} of them (1 to 100). */
    @GetMapping("/orders")
    List<Order> list(@RequestParam(defaultValue = "20") int limit, @AuthenticationPrincipal Jwt jwt) {
        return orders.list(jwt.getSubject(), limit);
    }

    /** Callers only see their own orders; anyone else's order is a 404. */
    @GetMapping("/orders/{id}")
    ResponseEntity<Order> get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(orders.find(id, jwt.getSubject()));
    }

    /** Cancels a pending order, or cancels and refunds a paid one. Declined or cancelled orders return 409. */
    @PostMapping("/orders/{id}/cancel")
    ResponseEntity<Order> cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(orders.cancel(id, jwt.getSubject()));
    }
}
