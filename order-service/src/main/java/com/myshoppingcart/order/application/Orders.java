package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;
import com.myshoppingcart.order.domain.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: order storage. */
public interface Orders {

    /**
     * Stores a new order and records its OrderCreated event atomically. {@code idempotencyKey} may
     * be null; throws {@link DuplicateIdempotencyKeyException} if the customer already has an order
     * under that key, and then stores nothing. {@code customerEmail} (may be null) goes into the
     * event only.
     */
    void create(Order order, String idempotencyKey, String customerEmail);

    Optional<Order> findById(UUID id);

    Optional<Order> findByIdempotencyKey(String customerId, String idempotencyKey);

    /** The customer's most recent orders, newest first. */
    List<Order> findByCustomer(String customerId, int limit);

    /** Every customer's most recent orders, newest first; only those with {@code status} unless it is null. */
    List<Order> findAll(OrderStatus status, int limit);

    /**
     * Saves {@code after} only if the stored order still has the status of {@code before}.
     * Returns false if another change got there first, so the caller can reload and decide again.
     */
    boolean transition(Order before, Order after);

    /** Records a RefundRequested event for the order, in the caller's transaction. */
    void requestRefund(Order order);

    /** Records an OrderCancelled event, so inventory puts the order's stock back; in the caller's transaction. */
    void announceCancellation(Order order);
}
