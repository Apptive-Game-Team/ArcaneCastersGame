package com.wordonline.server.auth.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the same key {@code application.yml} declares under {@code team6515.server.account},
 * and checks the JSON Web Key Set URI {@link WebSecurityConfig} hands to
 * {@code NimbusJwtDecoder.withJwkSetUri(...)}. Nothing here starts a Spring context.
 */
class AccountPropertiesTest {

    @Test
    void bindsTheKeyDeclaredInApplicationYml() {
        AccountProperties properties = bind(Map.of("team6515.server.account.url", "http://account-server:8080"));

        assertThat(properties.url()).isEqualTo("http://account-server:8080");
        assertThat(properties.jwksUri()).isEqualTo("http://account-server:8080/.well-known/jwks");
    }

    @Test
    void trailingSlashInUrlDoesNotDoubleUpBeforeTheJwksPath() {
        AccountProperties properties = bind(Map.of("team6515.server.account.url", "http://account-server:8080/"));

        assertThat(properties.url()).isEqualTo("http://account-server:8080");
        assertThat(properties.jwksUri()).isEqualTo("http://account-server:8080/.well-known/jwks");
    }

    @Test
    void repeatedTrailingSlashesAreAllStripped() {
        AccountProperties properties = bind(Map.of("team6515.server.account.url", "http://account-server:8080///"));

        assertThat(properties.jwksUri()).isEqualTo("http://account-server:8080/.well-known/jwks");
    }

    private AccountProperties bind(Map<String, Object> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("team6515.server.account", AccountProperties.class)
                .orElseThrow(() -> new AssertionError("account properties did not bind"));
    }
}
