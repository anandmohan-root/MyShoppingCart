package com.myshoppingcart.order.application;

import java.util.Map;
import java.util.UUID;

/** Outbound port: each customer's cart. */
public interface Carts {

    /**
     * A cart's contents. {@code id} is created with the cart's first product and stays until the
     * cart is cleared, so an order placed from the cart can be recognised; null if there is no cart.
     */
    record Cart(UUID id, Map<UUID, Integer> quantities) {
    }

    /** The customer's cart; empty with a null id if there is none. */
    Cart get(String customerId);

    /** Sets the product's quantity, adding the product (and creating the cart) if needed. */
    void put(String customerId, UUID productId, int quantity);

    void remove(String customerId, UUID productId);

    void clear(String customerId);
}
