package com.myshoppingcart.catalog.application;

import com.myshoppingcart.catalog.domain.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: where products come from. */
public interface Products {

    List<Product> findAll();

    Optional<Product> findById(UUID id);
}
