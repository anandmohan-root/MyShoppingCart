package com.example.shop.order.adapter.out.persistence;

import com.example.shop.order.application.Orders;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcOrders implements Orders {

    static final String ORDER_CREATED_TOPIC = "order-created";

    /** The OrderCreated message contract, as consumed by payment-service. */
    record OrderCreated(UUID orderId, String customerId, BigDecimal amount) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcOrders(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Transactional outbox: the order row and its event commit together or not at all. */
    @Override
    @Transactional
    public void create(Order order) {
        jdbc.sql("""
                        INSERT INTO orders (id, customer_id, product_id, quantity, total, status)
                        VALUES (:id, :customerId, :productId, :quantity, :total, :status)""")
                .param("id", order.id())
                .param("customerId", order.customerId())
                .param("productId", order.productId())
                .param("quantity", order.quantity())
                .param("total", order.total())
                .param("status", order.status().name())
                .update();

        jdbc.sql("INSERT INTO outbox (topic, message_key, payload) VALUES (:topic, :key, :payload)")
                .param("topic", ORDER_CREATED_TOPIC)
                .param("key", order.id().toString())
                .param("payload", toJson(new OrderCreated(order.id(), order.customerId(), order.total())))
                .update();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jdbc.sql("SELECT id, customer_id, product_id, quantity, total, status FROM orders WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Order(
                        rs.getObject("id", UUID.class),
                        rs.getString("customer_id"),
                        rs.getObject("product_id", UUID.class),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("total"),
                        OrderStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    @Override
    public void updateStatus(Order order) {
        jdbc.sql("UPDATE orders SET status = :status WHERE id = :id")
                .param("status", order.status().name())
                .param("id", order.id())
                .update();
    }

    private String toJson(OrderCreated event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
