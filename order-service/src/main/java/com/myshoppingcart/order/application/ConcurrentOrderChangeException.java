package com.myshoppingcart.order.application;

import java.util.UUID;

/** The order changed between reading and saving it. A Kafka listener retries; an API caller gets 409. */
public class ConcurrentOrderChangeException extends RuntimeException {

    public ConcurrentOrderChangeException(UUID orderId) {
        super("order " + orderId + " was changed at the same moment, try again");
    }
}
