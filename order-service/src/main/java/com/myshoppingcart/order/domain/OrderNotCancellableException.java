package com.myshoppingcart.order.domain;

import java.util.UUID;

public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(UUID orderId, OrderStatus status) {
        super("order " + orderId + " is " + status + " and cannot be cancelled");
    }
}
