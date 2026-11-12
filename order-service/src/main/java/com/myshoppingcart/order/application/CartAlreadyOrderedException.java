package com.myshoppingcart.order.application;

import java.util.UUID;

/** An order already exists for this cart, so storing another one was refused. */
public class CartAlreadyOrderedException extends RuntimeException {

    public CartAlreadyOrderedException(UUID cartId) {
        super("cart " + cartId + " has already been ordered");
    }
}
