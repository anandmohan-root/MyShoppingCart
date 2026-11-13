package com.myshoppingcart.catalog.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** A product the shop sells, or used to sell: an off-sale product cannot be ordered. */
public record Product(UUID id, String name, BigDecimal price, boolean onSale) {

    public static final int MAX_NAME_LENGTH = 100;
    public static final BigDecimal MAX_PRICE = new BigDecimal("100000.00");

    public Product {
        if (name == null || name.isBlank() || name.strip().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0 || price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("price must be above 0 and at most " + MAX_PRICE + ", with at most 2 decimals");
        }
        name = name.strip();
    }

    public static Product create(String name, BigDecimal price) {
        return new Product(UUID.randomUUID(), name, price, true);
    }

    /** Applies the given changes; a null leaves that field as it is. */
    public Product changed(String newName, BigDecimal newPrice, Boolean newOnSale) {
        return new Product(id,
                newName == null ? name : newName,
                newPrice == null ? price : newPrice,
                newOnSale == null ? onSale : newOnSale);
    }
}
