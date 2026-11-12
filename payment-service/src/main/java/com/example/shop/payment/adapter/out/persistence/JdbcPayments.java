package com.example.shop.payment.adapter.out.persistence;

import com.example.shop.payment.application.Payments;
import com.example.shop.payment.domain.Payment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPayments implements Payments {

    private final JdbcClient jdbc;

    JdbcPayments(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The unique constraint on order_id is what makes the consumer idempotent. */
    @Override
    public boolean saveIfNew(Payment payment) {
        return jdbc.sql("""
                        INSERT INTO payment (id, order_id, amount) VALUES (:id, :orderId, :amount)
                        ON CONFLICT (order_id) DO NOTHING""")
                .param("id", payment.id())
                .param("orderId", payment.orderId())
                .param("amount", payment.amount())
                .update() == 1;
    }
}
