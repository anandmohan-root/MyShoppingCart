package com.example.shop.order.adapter.in.web;

import com.example.shop.order.application.OrderService;
import com.example.shop.order.application.UnknownProductException;
import com.example.shop.order.domain.Order;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@RestController
class OrderController {

    record PlaceOrderRequest(UUID productId, int quantity) {
    }

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    Order place(@RequestBody PlaceOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return orders.place(jwt.getSubject(), request.productId(), request.quantity());
    }

    /** Callers only see their own orders; anyone else's order is a 404. */
    @GetMapping("/orders/{id}")
    ResponseEntity<Order> get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(orders.find(id, jwt.getSubject()));
    }

    @ExceptionHandler({UnknownProductException.class, IllegalArgumentException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Catalog is down, slow, or its circuit breaker is open. */
    @ExceptionHandler({RestClientException.class, CallNotPermittedException.class})
    ProblemDetail catalogUnavailable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "catalog is unavailable, try again later");
    }
}
