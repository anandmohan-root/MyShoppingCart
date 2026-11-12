package com.example.shop.payment.application;

import com.example.shop.payment.domain.Payment;

/** Outbound port: tells the rest of the platform about payments. */
public interface PaymentEvents {

    void completed(Payment payment);
}
