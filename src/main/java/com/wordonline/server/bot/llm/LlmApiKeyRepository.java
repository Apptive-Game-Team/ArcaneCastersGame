package com.wordonline.server.bot.llm;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Reads the encrypted provider API keys the admin server writes to {@code llm_api_keys}, and
 * writes {@code unavailable_until} and {@code unavailable_reason} when a provider rejects a key.
 * Every time comparison happens in SQL against {@code CURRENT_TIMESTAMP}: the columns are
 * {@code timestamp without time zone} and the servers may run in different time zones.
 */
@Repository
@RequiredArgsConstructor
public class LlmApiKeyRepository {

    /** A stored key row. {@code unavailable} is true while {@code unavailable_until} is in the future. */
    public record StoredKey(String encryptedKey, boolean unavailable, String unavailableReason) {
    }

    private static final String FIND_STORED_KEY = """
            SELECT encrypted_key,
                   (unavailable_until IS NOT NULL AND unavailable_until > CURRENT_TIMESTAMP) AS unavailable,
                   unavailable_reason
            FROM llm_api_keys
            WHERE provider = :provider;
            """;

    private static final String MARK_UNAVAILABLE = """
            UPDATE llm_api_keys
            SET unavailable_until = CURRENT_TIMESTAMP + CAST(:minutes AS INTEGER) * INTERVAL '1' MINUTE,
                unavailable_reason = :reason
            WHERE provider = :provider;
            """;

    private final JdbcClient jdbcClient;

    public Optional<StoredKey> findStoredKey(String provider) {
        return jdbcClient.sql(FIND_STORED_KEY)
                .param("provider", provider)
                .query((rs, rowNum) -> new StoredKey(
                        rs.getString("encrypted_key"),
                        rs.getBoolean("unavailable"),
                        rs.getString("unavailable_reason")))
                .optional();
    }

    /** Marks the provider's key unavailable for {@code minutes} from now; returns the rows changed. */
    public int markUnavailable(String provider, long minutes, String reason) {
        return jdbcClient.sql(MARK_UNAVAILABLE)
                .param("provider", provider)
                .param("minutes", minutes)
                .param("reason", reason)
                .update();
    }
}
