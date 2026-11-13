package com.myshoppingcart.order.adapter.in.web;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless resource server: every request needs a valid Keycloak JWT, and /admin/** also needs
 * the Keycloak realm role "admin".
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter roles = new JwtAuthenticationConverter();
        roles.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoles());
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The end of a server-sent event stream is an async dispatch without the
                        // token; the request itself was already authorized.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/admin/**").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
                .build();
    }
}
