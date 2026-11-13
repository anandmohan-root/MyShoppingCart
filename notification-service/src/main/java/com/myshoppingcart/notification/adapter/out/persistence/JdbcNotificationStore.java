package com.myshoppingcart.notification.adapter.out.persistence;

import com.myshoppingcart.notification.application.NotificationStore;
import com.myshoppingcart.notification.application.OrderContact;
import com.myshoppingcart.notification.domain.Notification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcNotificationStore implements NotificationStore {

    private final JdbcClient jdbc;

    JdbcNotificationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveContact(OrderContact contact) {
        jdbc.sql("INSERT INTO order_contact (order_id, email, total) VALUES (:orderId, :email, :total) ON CONFLICT (order_id) DO NOTHING")
                .param("orderId", contact.orderId())
                .param("email", contact.email())
                .param("total", contact.total())
                .update();
    }

    @Override
    public Optional<OrderContact> findContact(UUID orderId) {
        return jdbc.sql("SELECT order_id, email, total FROM order_contact WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query((rs, n) -> new OrderContact(rs.getObject("order_id", UUID.class), rs.getString("email"), rs.getBigDecimal("total")))
                .optional();
    }

    @Override
    public boolean markSent(UUID orderId, Notification notification) {
        return jdbc.sql("INSERT INTO sent_notification (order_id, kind) VALUES (:orderId, :kind) ON CONFLICT DO NOTHING")
                .param("orderId", orderId)
                .param("kind", notification.name())
                .update() == 1;
    }
}
