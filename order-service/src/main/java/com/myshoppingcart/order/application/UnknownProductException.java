package com.myshoppingcart.order.application;

import java.util.UUID;

public class UnknownProductException extends RuntimeException {

    public UnknownProductException(UUID productId) {
        super("unknown product " + productId);
    }
}
