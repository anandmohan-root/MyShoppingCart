package com.myshoppingcart.payment.application;

import com.myshoppingcart.payment.domain.Payment;

import java.util.Optional;
import java.util.UUID;

/** Outbound port: payment storage. */
public interface Payments {

    /** Stores the payment unless one already exists for its order. Returns true if it was stored. */
    boolean saveIfNew(Payment payment);

    Optional<Payment> findByOrderId(UUID orderId);

    void updateStatus(Payment payment);
}
