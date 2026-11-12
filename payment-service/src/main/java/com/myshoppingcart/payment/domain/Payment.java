package com.myshoppingcart.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Payment(UUID id, UUID orderId, BigDecimal amount) {

    public static Payment forOrder(UUID orderId, BigDecimal amount) {
        return new Payment(UUID.randomUUID(), orderId, amount);
    }
}
