package com.myshoppingcart.payment.application;

import com.myshoppingcart.payment.domain.Payment;

/**
 * Outbound port: tells the rest of the platform about payments. Implementations must record
 * the event in the caller's transaction, so a payment and its event commit together.
 */
public interface PaymentEvents {

    void completed(Payment payment);

    void declined(Payment payment);
}
