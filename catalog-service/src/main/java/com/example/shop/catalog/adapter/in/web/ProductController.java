package com.example.shop.catalog.adapter.in.web;

import com.example.shop.catalog.application.Products;
import com.example.shop.catalog.domain.Product;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
class ProductController {

    private final Products products;

    ProductController(Products products) {
        this.products = products;
    }

    @GetMapping("/products")
    List<Product> all() {
        return products.findAll();
    }

    @GetMapping("/products/{id}")
    ResponseEntity<Product> one(@PathVariable UUID id) {
        return ResponseEntity.of(products.findById(id));
    }
}
