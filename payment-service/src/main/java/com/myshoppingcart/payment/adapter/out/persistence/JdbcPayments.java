package com.myshoppingcart.payment.adapter.out.persistence;

import com.myshoppingcart.payment.application.Payments;
import com.myshoppingcart.payment.domain.Payment;
import com.myshoppingcart.payment.domain.PaymentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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
                        INSERT INTO payment (id, order_id, amount, status) VALUES (:id, :orderId, :amount, :status)
                        ON CONFLICT (order_id) DO NOTHING""")
                .param("id", payment.id())
                .param("orderId", payment.orderId())
                .param("amount", payment.amount())
                .param("status", payment.status().name())
                .update() == 1;
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return jdbc.sql("SELECT id, order_id, amount, status FROM payment WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query((rs, n) -> new Payment(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        PaymentStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    @Override
    public void updateStatus(Payment payment) {
        jdbc.sql("UPDATE payment SET status = :status WHERE id = :id")
                .param("status", payment.status().name())
                .param("id", payment.id())
                .update();
    }
}
