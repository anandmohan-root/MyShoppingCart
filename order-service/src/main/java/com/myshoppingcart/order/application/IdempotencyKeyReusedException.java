package com.myshoppingcart.order.application;

/** The idempotency key was already used for an order with different items. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("this Idempotency-Key was already used for an order with different items");
    }
}
