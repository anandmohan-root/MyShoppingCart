package com.myshoppingcart.inventory.adapter.in.web;

import com.myshoppingcart.security.AccessRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Inventory's own access rules: restocking needs the Keycloak realm role "admin". Token checking
 * and everything else comes from shop-security.
 */
@Configuration
class SecurityConfig {

    @Bean
    AccessRules inventoryAccessRules() {
        return requests -> requests.requestMatchers(HttpMethod.POST, "/stock/*/restock").hasRole("admin");
    }
}
