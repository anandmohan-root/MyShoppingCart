package com.myshoppingcart.payment.application;

import com.myshoppingcart.payment.domain.Payment;

/** Outbound port: tells the rest of the platform about payments. */
public interface PaymentEvents {

    void completed(Payment payment);
}
