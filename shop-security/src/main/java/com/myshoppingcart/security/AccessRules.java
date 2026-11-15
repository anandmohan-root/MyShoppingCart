package com.myshoppingcart.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * A service's own access rules, such as "POST /products needs the admin role". Declare one as a
 * bean; everything not matched by a rule only needs a valid token.
 */
@FunctionalInterface
public interface AccessRules {

    void apply(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry requests);
}
