package com.myshoppingcart.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** One product in an order, at the price it had when the order was placed. */
public record OrderLine(UUID productId, int quantity, BigDecimal unitPrice) {

    public OrderLine {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
