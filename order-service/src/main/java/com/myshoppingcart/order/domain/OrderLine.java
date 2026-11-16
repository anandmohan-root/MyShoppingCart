package com.myshoppingcart.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One product in an order, with the name and price it had when the order was placed.
 * The name is null for orders placed before names were stored.
 */
public record OrderLine(UUID productId, String productName, int quantity, BigDecimal unitPrice) {

    public OrderLine {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
