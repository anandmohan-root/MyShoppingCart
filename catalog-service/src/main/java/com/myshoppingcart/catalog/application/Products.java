package com.myshoppingcart.catalog.application;

import com.myshoppingcart.catalog.domain.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: product storage. */
public interface Products {

    /** Products on sale, by name. */
    List<Product> findOnSale();

    /** Any product, on sale or not. */
    Optional<Product> findById(UUID id);

    /** Stores a new product and records its ProductCreated event, in one transaction. */
    void create(Product product);

    void update(Product product);
}
