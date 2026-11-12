package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;
import com.myshoppingcart.order.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final Orders orders;
    private final ProductCatalog catalog;

    public OrderService(Orders orders, ProductCatalog catalog) {
        this.orders = orders;
        this.catalog = catalog;
    }

    public Order place(String customerId, UUID productId, int quantity) {
        BigDecimal unitPrice = catalog.priceOf(productId).orElseThrow(() -> new UnknownProductException(productId));
        Order order = Order.place(customerId, productId, quantity, unitPrice);
        orders.create(order);
        return order;
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
