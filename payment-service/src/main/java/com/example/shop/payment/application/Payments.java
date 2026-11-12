package com.example.shop.payment.application;

import com.example.shop.payment.domain.Payment;

/** Outbound port: payment storage. */
public interface Payments {

    /** Stores the payment unless one already exists for its order. Returns true if it was stored. */
    boolean saveIfNew(Payment payment);
}
