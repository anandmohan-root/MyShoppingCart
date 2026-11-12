package com.example.shop.order.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: price lookup in the catalog. */
public interface ProductCatalog {

    Optional<BigDecimal> priceOf(UUID productId);
}
