package com.myshoppingcart.order.adapter.in.web;

import com.myshoppingcart.security.AccessRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Order's own access rules: /admin/** needs the Keycloak realm role "admin". Token checking and
 * everything else comes from shop-security.
 */
@Configuration
class SecurityConfig {

    @Bean
    AccessRules orderAccessRules() {
        return requests -> requests.requestMatchers("/admin/**").hasRole("admin");
    }
}
