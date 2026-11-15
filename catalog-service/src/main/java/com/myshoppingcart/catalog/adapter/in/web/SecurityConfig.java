package com.myshoppingcart.catalog.adapter.in.web;

import com.myshoppingcart.security.AccessRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Catalog's own access rules: adding or changing products needs the Keycloak realm role "admin".
 * Token checking and everything else comes from shop-security.
 */
@Configuration
class SecurityConfig {

    @Bean
    AccessRules catalogAccessRules() {
        return requests -> requests
                .requestMatchers(HttpMethod.POST, "/products").hasRole("admin")
                .requestMatchers(HttpMethod.PATCH, "/products/*").hasRole("admin");
    }
}
