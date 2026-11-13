package com.myshoppingcart.order.adapter.in.web;

import com.myshoppingcart.order.application.OrderService;
import com.myshoppingcart.order.domain.Order;
import com.myshoppingcart.order.domain.OrderStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Every customer's orders, for the Keycloak realm role "admin" only (see SecurityConfig). */
@RestController
class AdminOrderController {

    private final OrderService orders;

    AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Newest first, at most {@code limit} (1 to 100); only one status if {@code status} is given. */
    @GetMapping("/admin/orders")
    List<Order> all(@RequestParam(required = false) OrderStatus status, @RequestParam(defaultValue = "50") int limit) {
        return orders.listAll(status, limit);
    }

    /** Cancels any customer's pending or paid order; a paid order is refunded. Others return 409. */
    @PostMapping("/admin/orders/{id}/cancel")
    ResponseEntity<Order> cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(orders.cancelAsAdmin(id, jwt.getClaimAsString("preferred_username")));
    }
}
