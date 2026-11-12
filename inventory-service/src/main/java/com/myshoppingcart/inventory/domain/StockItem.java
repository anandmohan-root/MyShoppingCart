package com.myshoppingcart.inventory.domain;

import java.util.UUID;

/** How many of a product can still be sold. */
public record StockItem(UUID productId, int available) {
}
