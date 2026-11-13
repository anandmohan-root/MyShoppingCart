package com.myshoppingcart.notification.application;

import java.math.BigDecimal;
import java.util.UUID;

/** What this service knows about an order: where to write and how much it came to. */
public record OrderContact(UUID orderId, String email, BigDecimal total) {
}
