package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: order storage. */
public interface Orders {

    /** Stores a new order and records its OrderCreated event atomically. */
    void create(Order order);

    Optional<Order> findById(UUID id);

    /** The customer's most recent orders, newest first. */
    List<Order> findByCustomer(String customerId, int limit);

    /**
     * Saves {@code after} only if the stored order still has the status of {@code before}.
     * Returns false if another change got there first, so the caller can reload and decide again.
     */
    boolean transition(Order before, Order after);

    /** Records a RefundRequested event for the order, in the caller's transaction. */
    void requestRefund(Order order);
}
