package com.wordonline.server.bot.llm;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Reads the encrypted provider API keys the admin server writes to {@code llm_api_keys}. */
@Repository
@RequiredArgsConstructor
public class LlmApiKeyRepository {

    private static final String FIND_ENCRYPTED_KEY = """
            SELECT encrypted_key
            FROM llm_api_keys
            WHERE provider = :provider;
            """;

    private final JdbcClient jdbcClient;

    public Optional<String> findEncryptedKey(String provider) {
        return jdbcClient.sql(FIND_ENCRYPTED_KEY)
                .param("provider", provider)
                .query(String.class)
                .optional();
    }
}
