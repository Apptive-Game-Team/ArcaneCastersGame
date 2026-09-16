package com.wordonline.server.auth.config;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies the {@code jwtDecoder} bean is wired to account's JSON Web Key Set URI, without
 * making any network call: {@code NimbusJwtDecoder.withJwkSetUri(...)} is stubbed out, so
 * building the bean never resolves a real host.
 */
class WebSecurityConfigTest {

    @Test
    void jwtDecoderIsBuiltFromAccountsJwkSetUri() {
        AccountProperties accountProperties = new AccountProperties("http://account-server:8080/");
        WebSecurityConfig webSecurityConfig = new WebSecurityConfig(accountProperties);

        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = mock(NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder.class);
        NimbusJwtDecoder builtDecoder = mock(NimbusJwtDecoder.class);
        when(builder.build()).thenReturn(builtDecoder);

        try (MockedStatic<NimbusJwtDecoder> nimbusJwtDecoder = mockStatic(NimbusJwtDecoder.class)) {
            nimbusJwtDecoder.when(() -> NimbusJwtDecoder.withJwkSetUri("http://account-server:8080/.well-known/jwks"))
                    .thenReturn(builder);

            JwtDecoder decoder = webSecurityConfig.jwtDecoder();

            assertThat(decoder).isSameAs(builtDecoder);
            nimbusJwtDecoder.verify(() -> NimbusJwtDecoder.withJwkSetUri("http://account-server:8080/.well-known/jwks"));
        }
    }
}
