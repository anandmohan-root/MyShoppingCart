package com.example.shop.catalog.adapter.out.persistence;

import com.example.shop.catalog.application.Products;
import com.example.shop.catalog.domain.Product;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcProducts implements Products {

    private static final RowMapper<Product> MAPPER = (rs, n) ->
            new Product(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBigDecimal("price"));

    private final JdbcClient jdbc;

    JdbcProducts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Product> findAll() {
        return jdbc.sql("SELECT id, name, price FROM product ORDER BY name").query(MAPPER).list();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jdbc.sql("SELECT id, name, price FROM product WHERE id = :id").param("id", id).query(MAPPER).optional();
    }
}
