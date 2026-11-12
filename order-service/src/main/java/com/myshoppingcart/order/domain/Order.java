package com.myshoppingcart.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * An order for one or more products; each product appears on one line only.
 *
 * <pre>
 * PENDING --payment completed--> PAID
 * PENDING --payment declined---> PAYMENT_DECLINED
 * PENDING --stock rejected-----> OUT_OF_STOCK
 * PENDING or PAID --customer---> CANCELLED (a paid order is refunded)
 * </pre>
 */
public record Order(UUID id, String customerId, List<OrderLine> lines, BigDecimal total, OrderStatus status,
                    Instant createdAt) {

    /** Each line costs one catalog lookup when the order is placed, so the number of lines is capped. */
    public static final int MAX_LINES = 20;

    public Order {
        lines = List.copyOf(lines);
    }

    public static Order place(String customerId, List<OrderLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("an order can have at most " + MAX_LINES + " lines");
        }
        if (new HashSet<>(lines.stream().map(OrderLine::productId).toList()).size() != lines.size()) {
            throw new IllegalArgumentException("each product can appear on one line only");
        }
        BigDecimal total = lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        // PostgreSQL stores microseconds; truncating keeps the returned order equal to the stored one.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Order(UUID.randomUUID(), customerId, lines, total, OrderStatus.PENDING, now);
    }

    /** Only a pending order becomes paid; for any other status the payment changes nothing here. */
    public Order paid() {
        return status == OrderStatus.PENDING ? withStatus(OrderStatus.PAID) : this;
    }

    /** Only a pending order is declined; for any other status the decline changes nothing here. */
    public Order declined() {
        return status == OrderStatus.PENDING ? withStatus(OrderStatus.PAYMENT_DECLINED) : this;
    }

    /** Only a pending order runs out of stock; for any other status the rejection changes nothing here. */
    public Order outOfStock() {
        return status == OrderStatus.PENDING ? withStatus(OrderStatus.OUT_OF_STOCK) : this;
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
        return new Order(id, customerId, lines, total, newStatus, createdAt);
    }
}
