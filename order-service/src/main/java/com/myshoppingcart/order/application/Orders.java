package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;

import java.util.Optional;
import java.util.UUID;

/** Outbound port: order storage. */
public interface Orders {

    /** Stores a new order and records its OrderCreated event atomically. */
    void create(Order order);

    Optional<Order> findById(UUID id);

    void updateStatus(Order order);
}
