package com.myshoppingcart.order.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: product lookup in the catalog. */
public interface ProductCatalog {

    /** A product as it is on sale now. */
    record Product(String name, BigDecimal price) {
    }

    /** Empty when the product is unknown or not for sale. */
    Optional<Product> find(UUID productId);
}
