package com.myshoppingcart.order.application;

/** The customer already has an order stored under this idempotency key, so nothing was stored. */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("an order with idempotency key " + idempotencyKey + " already exists");
    }
}
