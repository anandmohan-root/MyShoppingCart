package com.myshoppingcart.payment.application;

import com.myshoppingcart.payment.domain.Payment;
import com.myshoppingcart.payment.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private final Payments payments;
    private final PaymentEvents events;
    private final BigDecimal declineAbove;

    public PaymentService(Payments payments, PaymentEvents events, @Value("${payment.decline-above}") BigDecimal declineAbove) {
        this.payments = payments;
        this.events = events;
        this.declineAbove = declineAbove;
    }

    /**
     * Idempotent consumer: a redelivered OrderCreated finds the payment already stored and does
     * nothing. The payment and its PaymentCompleted or PaymentDeclined event commit together.
     */
    @Transactional
    public void payFor(UUID orderId, BigDecimal amount) {
        Payment payment = Payment.charge(orderId, amount, declineAbove);
        if (!payments.saveIfNew(payment)) {
            return;
        }
        if (payment.status() == PaymentStatus.COMPLETED) {
            events.completed(payment);
        } else {
            events.declined(payment);
        }
    }

    /** Compensation for a cancelled order. Safe to repeat: a refunded payment stays refunded. */
    @Transactional
    public void refund(UUID orderId) {
        payments.findByOrderId(orderId)
                .filter(payment -> payment.status() == PaymentStatus.COMPLETED)
                .map(Payment::refunded)
                .ifPresent(payments::updateStatus);
    }
}
