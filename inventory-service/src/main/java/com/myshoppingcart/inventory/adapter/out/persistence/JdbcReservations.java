package com.myshoppingcart.inventory.adapter.out.persistence;

import com.myshoppingcart.inventory.application.Reservations;
import com.myshoppingcart.inventory.domain.OrderLine;
import com.myshoppingcart.inventory.domain.ReservationStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JdbcReservations implements Reservations {

    private final JdbcClient jdbc;

    JdbcReservations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The primary key on order_id decides which event about an order came first. */
    @Override
    public boolean createIfNew(UUID orderId, ReservationStatus status, List<OrderLine> lines) {
        boolean created = jdbc.sql("""
                        INSERT INTO reservation (order_id, status) VALUES (:orderId, :status)
                        ON CONFLICT (order_id) DO NOTHING""")
                .param("orderId", orderId)
                .param("status", status.name())
                .update() == 1;
        if (created) {
            for (OrderLine line : lines) {
                jdbc.sql("INSERT INTO reservation_line (order_id, product_id, quantity) VALUES (:orderId, :productId, :quantity)")
                        .param("orderId", orderId)
                        .param("productId", line.productId())
                        .param("quantity", line.quantity())
                        .update();
            }
        }
        return created;
    }

    @Override
    public List<OrderLine> release(UUID orderId) {
        boolean released = jdbc.sql("UPDATE reservation SET status = 'RELEASED' WHERE order_id = :orderId AND status = 'RESERVED'")
                .param("orderId", orderId)
                .update() == 1;
        if (!released) {
            return List.of();
        }
        return jdbc.sql("SELECT product_id, quantity FROM reservation_line WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query((rs, n) -> new OrderLine(rs.getObject("product_id", UUID.class), rs.getInt("quantity")))
                .list();
    }
}
