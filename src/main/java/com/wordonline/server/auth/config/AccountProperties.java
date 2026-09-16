package com.wordonline.server.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this server reaches the account server that mints and signs its JWTs.
 *
 * <p>This server never mints a JWT itself; it only verifies the ones account issues, by
 * fetching account's JSON Web Key Set. Keeping the key material on account alone means a key
 * rotation ends there instead of requiring every game server container to be updated and
 * restarted.
 */
@ConfigurationProperties(prefix = "team6515.server.account")
public record AccountProperties(String url) {

    private static final String JWKS_PATH = "/.well-known/jwks";

    public AccountProperties {
        url = stripTrailingSlashes(url);
    }

    /**
     * @return the JSON Web Key Set endpoint {@link org.springframework.security.oauth2.jwt.NimbusJwtDecoder}
     *         fetches (and caches) to verify JWTs signed by account.
     */
    public String jwksUri() {
        return url + JWKS_PATH;
    }

    private static String stripTrailingSlashes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
