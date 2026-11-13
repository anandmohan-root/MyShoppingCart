package com.myshoppingcart.catalog.adapter.in.web;

import com.myshoppingcart.catalog.application.CatalogService;
import com.myshoppingcart.catalog.domain.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Products on sale for everyone; adding and changing products for admins (see SecurityConfig). */
@RestController
class ProductController {

    record NewProduct(String name, BigDecimal price) {
    }

    /** Only the fields present are changed. */
    record ProductChanges(String name, BigDecimal price, Boolean onSale) {
    }

    private final CatalogService catalog;

    ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    List<Product> all() {
        return catalog.onSale();
    }

    /** Off-sale products are not found, so they cannot be ordered or put in a cart. */
    @GetMapping("/products/{id}")
    ResponseEntity<Product> one(@PathVariable UUID id) {
        return ResponseEntity.of(catalog.findOnSale(id));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    Product create(@RequestBody NewProduct request, @AuthenticationPrincipal Jwt jwt) {
        return catalog.create(request.name(), request.price(), jwt.getClaimAsString("preferred_username"));
    }

    @PatchMapping("/products/{id}")
    ResponseEntity<Product> change(@PathVariable UUID id, @RequestBody ProductChanges changes, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(catalog.change(id, changes.name(), changes.price(), changes.onSale(),
                jwt.getClaimAsString("preferred_username")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
