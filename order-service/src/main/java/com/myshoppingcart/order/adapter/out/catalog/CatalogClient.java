package com.myshoppingcart.order.adapter.out.catalog;

import com.myshoppingcart.order.application.ProductCatalog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls catalog-service, relaying the caller's JWT. The call has a timeout (spring.http.client
 * in application.yml), is retried, and sits behind a circuit breaker (resilience4j in
 * application.yml). A 404 is a normal answer, not a failure, so it is handled inside the method.
 */
@Component
class CatalogClient implements ProductCatalog {

    private record ProductResponse(BigDecimal price) {
    }

    private final RestClient rest;

    CatalogClient(RestClient.Builder builder, @Value("${catalog.url}") String catalogUrl) {
        this.rest = builder.baseUrl(catalogUrl).build();
    }

    @Override
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public Optional<BigDecimal> priceOf(UUID productId) {
        var caller = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        try {
            ProductResponse product = rest.get().uri("/products/{id}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.getToken().getTokenValue())
                    .retrieve()
                    .body(ProductResponse.class);
            return Optional.of(product.price());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
