package com.myshoppingcart.inventory.adapter.out.persistence;

import com.myshoppingcart.inventory.application.Stock;
import com.myshoppingcart.inventory.domain.OrderLine;
import com.myshoppingcart.inventory.domain.StockItem;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcStock implements Stock {

    private static final RowMapper<StockItem> MAPPER = (rs, n) ->
            new StockItem(rs.getObject("product_id", UUID.class), rs.getInt("available"));

    private final JdbcClient jdbc;

    JdbcStock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StockItem> findAll() {
        return jdbc.sql("SELECT product_id, available FROM stock ORDER BY product_id").query(MAPPER).list();
    }

    @Override
    public Optional<StockItem> find(UUID productId) {
        return jdbc.sql("SELECT product_id, available FROM stock WHERE product_id = :id")
                .param("id", productId)
                .query(MAPPER)
                .optional();
    }

    /** Rows are locked in product id order, so two orders locking the same products cannot deadlock. */
    @Override
    public Map<UUID, Integer> lockAvailable(Collection<UUID> productIds) {
        Map<UUID, Integer> available = new HashMap<>();
        if (productIds.isEmpty()) {
            return available;
        }
        jdbc.sql("SELECT product_id, available FROM stock WHERE product_id IN (:ids) ORDER BY product_id FOR UPDATE")
                .param("ids", productIds)
                .query(MAPPER)
                .list()
                .forEach(item -> available.put(item.productId(), item.available()));
        return available;
    }

    @Override
    public void take(List<OrderLine> lines) {
        change(lines, -1);
    }

    @Override
    public void putBack(List<OrderLine> lines) {
        change(lines, 1);
    }

    private void change(List<OrderLine> lines, int sign) {
        for (OrderLine line : lines) {
            jdbc.sql("UPDATE stock SET available = available + :delta WHERE product_id = :id")
                    .param("delta", sign * line.quantity())
                    .param("id", line.productId())
                    .update();
        }
    }
}
