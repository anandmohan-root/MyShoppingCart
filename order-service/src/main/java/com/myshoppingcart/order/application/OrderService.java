package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;
import com.myshoppingcart.order.domain.OrderLine;
import com.myshoppingcart.order.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    public static final int MAX_LIST = 100;

    private final Orders orders;
    private final ProductCatalog catalog;

    public OrderService(Orders orders, ProductCatalog catalog) {
        this.orders = orders;
        this.catalog = catalog;
    }

    public Order place(String customerId, List<OrderItem> items) {
        if (items.size() > Order.MAX_LINES) {
            throw new IllegalArgumentException("an order can have at most " + Order.MAX_LINES + " lines");
        }
        List<OrderLine> lines = items.stream()
                .map(item -> new OrderLine(item.productId(), item.quantity(), priceOf(item.productId())))
                .toList();
        Order order = Order.place(customerId, lines);
        orders.create(order);
        return order;
    }

    private BigDecimal priceOf(UUID productId) {
        return catalog.priceOf(productId).orElseThrow(() -> new UnknownProductException(productId));
    }

    /** The caller's own orders, newest first. {@code limit} is kept between 1 and {@link #MAX_LIST}. */
    public List<Order> list(String customerId, int limit) {
        return orders.findByCustomer(customerId, Math.clamp(limit, 1, MAX_LIST));
    }

    public Optional<Order> find(UUID orderId, String customerId) {
        return orders.findById(orderId).filter(order -> order.customerId().equals(customerId));
    }

    /**
     * Customer cancellation. A pending order is simply cancelled; a paid order is cancelled and
     * refunded (compensation). Throws {@link ConcurrentOrderChangeException} if a payment event
     * changed the order at the same moment, so the customer can try again.
     */
    @Transactional
    public Optional<Order> cancel(UUID orderId, String customerId) {
        return find(orderId, customerId).map(order -> {
            Order cancelled = order.cancelled();
            if (!orders.transition(order, cancelled)) {
                throw new ConcurrentOrderChangeException(orderId);
            }
            if (order.status() == OrderStatus.PAID) {
                orders.requestRefund(order);
            }
            return cancelled;
        });
    }

    /**
     * Safe to call more than once: Kafka delivers at least once. If the customer cancelled while
     * the payment was in flight, the money is refunded instead of the order being marked paid.
     */
    @Transactional
    public void paymentCompleted(UUID orderId) {
        orders.findById(orderId).ifPresent(order -> {
            if (order.status() == OrderStatus.CANCELLED) {
                orders.requestRefund(order);
            } else if (!orders.transition(order, order.paid())) {
                throw new ConcurrentOrderChangeException(orderId);
            }
        });
    }

    /** Safe to call more than once. A declined payment needs no compensation: nothing was charged. */
    @Transactional
    public void paymentDeclined(UUID orderId) {
        orders.findById(orderId).ifPresent(order -> {
            if (!orders.transition(order, order.declined())) {
                throw new ConcurrentOrderChangeException(orderId);
            }
        });
    }
}
