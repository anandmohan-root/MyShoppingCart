package com.myshoppingcart.order.adapter.out.persistence;

import com.myshoppingcart.order.application.DuplicateIdempotencyKeyException;
import com.myshoppingcart.order.application.Orders;
import com.myshoppingcart.order.domain.Order;
import com.myshoppingcart.order.domain.OrderLine;
import com.myshoppingcart.order.domain.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class JdbcOrders implements Orders {

    static final String ORDER_CREATED_TOPIC = "order-created";
    static final String REFUND_REQUESTED_TOPIC = "refund-requested";
    static final String ORDER_CANCELLED_TOPIC = "order-cancelled";

    /** The OrderCreated message contract, as consumed by inventory-service. */
    record OrderCreated(UUID orderId, String customerId, BigDecimal amount, List<Line> lines) {

        record Line(UUID productId, int quantity) {
        }
    }

    /** The OrderCancelled message contract, as consumed by inventory-service. */
    record OrderCancelled(UUID orderId) {
    }

    /** The RefundRequested message contract, as consumed by payment-service. */
    record RefundRequested(UUID orderId, BigDecimal amount) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcOrders(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Transactional outbox: the order row and its event commit together or not at all. The unique
     * index on (customer_id, idempotency_key) makes a second order under the same key fail here,
     * even when two requests with that key run at the same moment.
     */
    @Override
    @Transactional
    public void create(Order order, String idempotencyKey) {
        try {
            jdbc.sql("""
                            INSERT INTO orders (id, customer_id, total, status, created_at, idempotency_key)
                            VALUES (:id, :customerId, :total, :status, :createdAt, :idempotencyKey)""")
                    .param("id", order.id())
                    .param("customerId", order.customerId())
                    .param("total", order.total())
                    .param("status", order.status().name())
                    .param("createdAt", Timestamp.from(order.createdAt()))
                    .param("idempotencyKey", idempotencyKey)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateIdempotencyKeyException(idempotencyKey);
        }

        for (int i = 0; i < order.lines().size(); i++) {
            OrderLine line = order.lines().get(i);
            jdbc.sql("""
                            INSERT INTO order_line (order_id, line_no, product_id, quantity, unit_price)
                            VALUES (:orderId, :lineNo, :productId, :quantity, :unitPrice)""")
                    .param("orderId", order.id())
                    .param("lineNo", i + 1)
                    .param("productId", line.productId())
                    .param("quantity", line.quantity())
                    .param("unitPrice", line.unitPrice())
                    .update();
        }

        jdbc.sql("INSERT INTO outbox (topic, message_key, payload) VALUES (:topic, :key, :payload)")
                .param("topic", ORDER_CREATED_TOPIC)
                .param("key", order.id().toString())
                .param("payload", toJson(new OrderCreated(order.id(), order.customerId(), order.total(),
                        order.lines().stream().map(l -> new OrderCreated.Line(l.productId(), l.quantity())).toList())))
                .update();
    }

    private static final String COLUMNS = "id, customer_id, total, status, created_at";

    /** An order row before its lines are attached. */
    private record OrderRow(UUID id, String customerId, BigDecimal total, OrderStatus status, Instant createdAt) {
    }

    private record LineRow(UUID orderId, OrderLine line) {
    }

    private static final RowMapper<OrderRow> ORDER_MAPPER = (rs, n) -> new OrderRow(
            rs.getObject("id", UUID.class),
            rs.getString("customer_id"),
            rs.getBigDecimal("total"),
            OrderStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<LineRow> LINE_MAPPER = (rs, n) -> new LineRow(
            rs.getObject("order_id", UUID.class),
            new OrderLine(rs.getObject("product_id", UUID.class), rs.getInt("quantity"), rs.getBigDecimal("unit_price")));

    @Override
    public Optional<Order> findById(UUID id) {
        return withLines(jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE id = :id")
                .param("id", id)
                .query(ORDER_MAPPER)
                .list())
                .stream().findFirst();
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String customerId, String idempotencyKey) {
        return withLines(jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE customer_id = :customerId AND idempotency_key = :key")
                .param("customerId", customerId)
                .param("key", idempotencyKey)
                .query(ORDER_MAPPER)
                .list())
                .stream().findFirst();
    }

    @Override
    public List<Order> findByCustomer(String customerId, int limit) {
        return withLines(jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE customer_id = :customerId ORDER BY created_at DESC LIMIT :limit")
                .param("customerId", customerId)
                .param("limit", limit)
                .query(ORDER_MAPPER)
                .list());
    }

    /** Loads the lines of all the given orders in one query, keeping the orders' sequence. */
    private List<Order> withLines(List<OrderRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderLine>> lines = jdbc.sql("""
                        SELECT order_id, product_id, quantity, unit_price FROM order_line
                        WHERE order_id IN (:ids) ORDER BY order_id, line_no""")
                .param("ids", rows.stream().map(OrderRow::id).toList())
                .query(LINE_MAPPER)
                .list().stream()
                .collect(Collectors.groupingBy(LineRow::orderId,
                        Collectors.mapping(LineRow::line, Collectors.toList())));
        return rows.stream()
                .map(r -> new Order(r.id(), r.customerId(), lines.getOrDefault(r.id(), List.of()), r.total(), r.status(), r.createdAt()))
                .toList();
    }

    /** Compare-and-set on the status, so a concurrent change is detected instead of overwritten. */
    @Override
    public boolean transition(Order before, Order after) {
        return jdbc.sql("UPDATE orders SET status = :after WHERE id = :id AND status = :before")
                .param("after", after.status().name())
                .param("id", before.id())
                .param("before", before.status().name())
                .update() == 1;
    }

    @Override
    public void requestRefund(Order order) {
        outbox(REFUND_REQUESTED_TOPIC, order, new RefundRequested(order.id(), order.total()));
    }

    @Override
    public void announceCancellation(Order order) {
        outbox(ORDER_CANCELLED_TOPIC, order, new OrderCancelled(order.id()));
    }

    private void outbox(String topic, Order order, Object event) {
        jdbc.sql("INSERT INTO outbox (topic, message_key, payload) VALUES (:topic, :key, :payload)")
                .param("topic", topic)
                .param("key", order.id().toString())
                .param("payload", toJson(event))
                .update();
    }

    private String toJson(Object event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
