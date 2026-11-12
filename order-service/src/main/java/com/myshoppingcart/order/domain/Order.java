package com.myshoppingcart.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A single-product order. Multi-line orders are not supported yet.
 *
 * <pre>
 * PENDING --payment completed--> PAID
 * PENDING --payment declined---> PAYMENT_DECLINED
 * PENDING or PAID --customer---> CANCELLED (a paid order is refunded)
 * </pre>
 */
public record Order(UUID id, String customerId, UUID productId, int quantity, BigDecimal total, OrderStatus status,
                    Instant createdAt) {

    public static Order place(String customerId, UUID productId, int quantity, BigDecimal unitPrice) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        // PostgreSQL stores microseconds; truncating keeps the returned order equal to the stored one.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Order(UUID.randomUUID(), customerId, productId, quantity, total, OrderStatus.PENDING, now);
    }

    /** Only a pending order becomes paid; for any other status the payment changes nothing here. */
    public Order paid() {
        return status == OrderStatus.PENDING ? withStatus(OrderStatus.PAID) : this;
    }

    /** Only a pending order is declined; for any other status the decline changes nothing here. */
    public Order declined() {
        return status == OrderStatus.PENDING ? withStatus(OrderStatus.PAYMENT_DECLINED) : this;
    }

    public boolean canBeCancelled() {
        return status == OrderStatus.PENDING || status == OrderStatus.PAID;
    }

    public Order cancelled() {
        if (!canBeCancelled()) {
            throw new OrderNotCancellableException(id, status);
        }
        return withStatus(OrderStatus.CANCELLED);
    }

    private Order withStatus(OrderStatus newStatus) {
        return new Order(id, customerId, productId, quantity, total, newStatus, createdAt);
    }
}
