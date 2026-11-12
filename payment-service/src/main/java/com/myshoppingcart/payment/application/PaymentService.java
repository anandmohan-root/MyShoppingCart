package com.myshoppingcart.payment.application;

import com.myshoppingcart.payment.domain.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private final Payments payments;
    private final PaymentEvents events;

    public PaymentService(Payments payments, PaymentEvents events) {
        this.payments = payments;
        this.events = events;
    }

    /**
     * Idempotent consumer: a redelivered OrderCreated finds the payment already stored and does
     * nothing. The event is published before the commit, so a failed publish rolls the payment
     * back and Kafka redelivers. Charging always succeeds here; there is no real payment provider.
     */
    @Transactional
    public void payFor(UUID orderId, BigDecimal amount) {
        Payment payment = Payment.forOrder(orderId, amount);
        if (payments.saveIfNew(payment)) {
            events.completed(payment);
        }
    }
}
