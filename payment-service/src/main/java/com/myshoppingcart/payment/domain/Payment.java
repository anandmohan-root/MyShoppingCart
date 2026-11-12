package com.myshoppingcart.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Payment(UUID id, UUID orderId, BigDecimal amount, PaymentStatus status) {

    /**
     * Charges an order. There is no real payment provider: a charge above the limit is declined,
     * which stands in for a card being refused.
     */
    public static Payment charge(UUID orderId, BigDecimal amount, BigDecimal declineAbove) {
        PaymentStatus status = amount.compareTo(declineAbove) > 0 ? PaymentStatus.DECLINED : PaymentStatus.COMPLETED;
        return new Payment(UUID.randomUUID(), orderId, amount, status);
    }

    /** Only a completed payment can be refunded; refunding twice changes nothing. */
    public Payment refunded() {
        return status == PaymentStatus.COMPLETED ? new Payment(id, orderId, amount, PaymentStatus.REFUNDED) : this;
    }
}
