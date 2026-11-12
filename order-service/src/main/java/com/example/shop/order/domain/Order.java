package com.example.shop.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** A single-product order. Multi-line orders are left out of MyShoppingCart. */
public record Order(UUID id, String customerId, UUID productId, int quantity, BigDecimal total, OrderStatus status) {

    public static Order place(String customerId, UUID productId, int quantity, BigDecimal unitPrice) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        return new Order(UUID.randomUUID(), customerId, productId, quantity, total, OrderStatus.PENDING);
    }

    public Order paid() {
        return new Order(id, customerId, productId, quantity, total, OrderStatus.PAID);
    }
}
