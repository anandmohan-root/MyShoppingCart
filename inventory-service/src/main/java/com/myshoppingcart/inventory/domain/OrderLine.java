package com.myshoppingcart.inventory.domain;

import java.util.UUID;

/** A product and quantity an order asks for. */
public record OrderLine(UUID productId, int quantity) {
}
