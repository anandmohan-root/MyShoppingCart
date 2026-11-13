package com.myshoppingcart.catalog.application;

import com.myshoppingcart.catalog.domain.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final Products products;

    public CatalogService(Products products) {
        this.products = products;
    }

    public List<Product> onSale() {
        return products.findOnSale();
    }

    /** A product customers can order; off-sale products are not found. */
    public Optional<Product> findOnSale(UUID id) {
        return products.findById(id).filter(Product::onSale);
    }

    /** Adds a product. Inventory opens its stock at 0 when it sees the ProductCreated event. */
    public Product create(String name, BigDecimal price, String by) {
        Product product = Product.create(name, price);
        products.create(product);
        log.info("{} added product {} '{}' at {}", by, product.id(), product.name(), product.price());
        return product;
    }

    /**
     * Changes a product's name, price or sale status; a null leaves that field as it is. A new
     * price applies to orders placed from now on.
     */
    @Transactional
    public Optional<Product> change(UUID id, String name, BigDecimal price, Boolean onSale, String by) {
        return products.findById(id).map(product -> {
            Product changed = product.changed(name, price, onSale);
            products.update(changed);
            log.info("{} changed product {} from {} to {}", by, id, product, changed);
            return changed;
        });
    }
}
