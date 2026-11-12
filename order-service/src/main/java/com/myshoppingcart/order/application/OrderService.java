package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;
import org.springframework.stereotype.Service;

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

    /** Safe to call more than once for the same order: Kafka delivers at least once. */
    public void markPaid(UUID orderId) {
        orders.findById(orderId).map(Order::paid).ifPresent(orders::updateStatus);
    }
}
