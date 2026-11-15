package com.myshoppingcart.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Makes a service a stateless resource server: every request needs a valid Keycloak JWT (checked
 * against spring.security.oauth2.resourceserver.jwt.*), Keycloak realm roles become ROLE_&lt;role&gt;,
 * and the service's {@link AccessRules} beans add role checks on top. Added to any service that
 * depends on shop-security; a service that declares its own SecurityFilterChain replaces it.
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ShopSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain shopSecurityFilterChain(HttpSecurity http, ObjectProvider<AccessRules> rules) throws Exception {
        JwtAuthenticationConverter roles = new JwtAuthenticationConverter();
        roles.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoles());
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> {
                    // The end of a server-sent event stream, and an error page, are later dispatches
                    // of a request that was already checked; they carry no token of their own.
                    requests.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll();
                    rules.orderedStream().forEach(rule -> rule.apply(requests));
                    requests.anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
                .build();
    }
}
