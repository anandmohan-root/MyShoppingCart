package com.myshoppingcart.order.application;

import java.util.UUID;

/** A product and quantity the customer asks for; the price is looked up when the order is placed. */
public record OrderItem(UUID productId, int quantity) {
}
