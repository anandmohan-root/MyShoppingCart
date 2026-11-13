package com.myshoppingcart.notification.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** The emails a customer gets about an order, one of each at most. */
public enum Notification {

    CONFIRMED("Your order is confirmed",
            "Thank you! Your order %s for %s is paid. We will let you know if anything changes."),
    PAYMENT_DECLINED("Your payment was declined",
            "We could not take the payment of %2$s for your order %1$s, so the order is closed. Nothing was charged."),
    OUT_OF_STOCK("Your order could not be filled",
            "Sorry, part of your order %1$s is out of stock, so the order is closed. Nothing was charged."),
    CANCELLED("Your order is cancelled",
            "Your order %1$s is cancelled, as you asked."),
    REFUND_STARTED("Your refund is on its way",
            "We are refunding %2$s for your order %1$s.");

    private final String subject;
    private final String body;

    Notification(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public String subject() {
        return subject;
    }

    /** The order is named by the first part of its id, the same way the storefront could show it. */
    public String body(UUID orderId, BigDecimal total) {
        return body.formatted(orderId.toString().substring(0, 8), total.setScale(2)) + "\n\nMyShoppingCart";
    }
}
