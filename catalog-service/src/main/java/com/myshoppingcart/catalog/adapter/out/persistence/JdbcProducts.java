package com.myshoppingcart.catalog.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.catalog.application.Products;
import com.myshoppingcart.catalog.domain.Product;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcProducts implements Products {

    static final String PRODUCT_CREATED_TOPIC = "product-created";

    /** The ProductCreated message contract, as consumed by inventory-service. */
    record ProductCreated(UUID productId, String name, BigDecimal price) {
    }

    private static final RowMapper<Product> MAPPER = (rs, n) -> new Product(
            rs.getObject("id", UUID.class), rs.getString("name"), rs.getBigDecimal("price"), rs.getBoolean("on_sale"));

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcProducts(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Product> findOnSale() {
        return jdbc.sql("SELECT id, name, price, on_sale FROM product WHERE on_sale ORDER BY name").query(MAPPER).list();
    }

    @Override
    public List<Product> findAll() {
        return jdbc.sql("SELECT id, name, price, on_sale FROM product ORDER BY name").query(MAPPER).list();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jdbc.sql("SELECT id, name, price, on_sale FROM product WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    /** Transactional outbox: the product row and its event commit together or not at all. */
    @Override
    @Transactional
    public void create(Product product) {
        jdbc.sql("INSERT INTO product (id, name, price, on_sale) VALUES (:id, :name, :price, :onSale)")
                .param("id", product.id())
                .param("name", product.name())
                .param("price", product.price())
                .param("onSale", product.onSale())
                .update();
        try {
            jdbc.sql("INSERT INTO outbox (topic, message_key, payload) VALUES (:topic, :key, :payload)")
                    .param("topic", PRODUCT_CREATED_TOPIC)
                    .param("key", product.id().toString())
                    .param("payload", json.writeValueAsString(new ProductCreated(product.id(), product.name(), product.price())))
                    .update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void update(Product product) {
        jdbc.sql("UPDATE product SET name = :name, price = :price, on_sale = :onSale WHERE id = :id")
                .param("name", product.name())
                .param("price", product.price())
                .param("onSale", product.onSale())
                .param("id", product.id())
                .update();
    }
}
