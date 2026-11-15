package com.myshoppingcart.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRolesTest {

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("alice");
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> roles(Jwt jwt) {
        return new KeycloakRealmRoles().convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void realmRolesBecomeSpringRoles() {
        assertThat(roles(token(Map.of("realm_access", Map.of("roles", List.of("admin", "default-roles-shop"))))))
                .containsExactly("ROLE_admin", "ROLE_default-roles-shop");
    }

    @Test
    void aTokenWithoutRealmRolesHasNoRoles() {
        assertThat(roles(token(Map.of("scope", "openid")))).isEmpty();
        assertThat(roles(token(Map.of("realm_access", Map.of("roles", "admin"))))).isEmpty();
    }
}
