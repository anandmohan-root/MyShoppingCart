package com.myshoppingcart.notification.application;

import java.util.UUID;

/**
 * An event about an order arrived before its OrderCreated was read (they come on different
 * topics). Throwing makes Kafka redeliver the event a little later.
 */
public class ContactNotYetKnownException extends RuntimeException {

    public ContactNotYetKnownException(UUID orderId) {
        super("no contact yet for order " + orderId);
    }
}
