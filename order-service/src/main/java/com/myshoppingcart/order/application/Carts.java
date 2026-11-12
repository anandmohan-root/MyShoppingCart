package com.myshoppingcart.order.application;

import java.util.Map;
import java.util.UUID;

/** Outbound port: each customer's cart, as product id to quantity. */
public interface Carts {

    /** The cart's products; empty if the customer has no cart. */
    Map<UUID, Integer> get(String customerId);

    /** Sets the product's quantity, adding the product if it is not in the cart yet. */
    void put(String customerId, UUID productId, int quantity);

    void remove(String customerId, UUID productId);

    void clear(String customerId);
}
