package com.wordonline.server.bot.llm;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wordonline.server.bot.domain.BotBrainType;

import lombok.extern.slf4j.Slf4j;

/**
 * Writes {@code unavailable_until} and {@code unavailable_reason} on a provider's
 * {@code llm_api_keys} row when the provider rejects the key (HTTP 401, 402, 403) or the stored
 * key cannot be decrypted. The lobby and the admin read those columns to stop matching bots whose
 * provider cannot answer.
 *
 * <p>Runs on the LLM request thread. It writes at most once per provider per
 * {@link #MIN_WRITE_INTERVAL_MILLIS}, so a match full of bots hitting the same bad key does not hit
 * the database once each, and a database failure is logged at WARN and never escapes. 429, 5xx and
 * timeouts are transient and are not recorded.
 */
@Slf4j
@Component
public class LlmKeyUnavailabilityRecorder {

    static final long MIN_WRITE_INTERVAL_MILLIS = 60_000;
    static final long INVALID_KEY_MINUTES = Duration.ofDays(30).toMinutes();
    static final int PROVIDER_MESSAGE_MAX_LENGTH = 150;
    static final String DECRYPT_FAILURE_REASON =
            "stored key cannot be decrypted with this server's LLM_KEY_SECRET";

    private final LlmApiKeyRepository repository;
    private final LlmBotProperties properties;
    private final LongSupplier clock;
    private final Map<BotBrainType, Long> nextWriteAtMillis = new EnumMap<>(BotBrainType.class);

    @Autowired
    public LlmKeyUnavailabilityRecorder(LlmApiKeyRepository repository, LlmBotProperties properties) {
        this(repository, properties, System::currentTimeMillis);
    }

    LlmKeyUnavailabilityRecorder(LlmApiKeyRepository repository, LlmBotProperties properties, LongSupplier clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Records a provider failure when its status says the key is rejected; any other failure is ignored. */
    public void recordFailure(BotBrainType provider, LlmCallFailure failure) {
        switch (failure.status()) {
            case 401 -> write(provider, INVALID_KEY_MINUTES, "HTTP 401: key rejected");
            case 402 -> write(provider, properties.unavailableMinutes(), "HTTP 402: balance empty");
            case 403 -> write(provider, properties.unavailableMinutes(),
                    "HTTP 403: " + truncate(failure.providerMessage(), PROVIDER_MESSAGE_MAX_LENGTH));
            default -> { }
        }
    }

    public void recordDecryptFailure(BotBrainType provider) {
        write(provider, INVALID_KEY_MINUTES, DECRYPT_FAILURE_REASON);
    }

    private void write(BotBrainType provider, long minutes, String reason) {
        long now = clock.getAsLong();
        // The slot is taken before the write, so a failing database is retried once a minute too.
        if (!takeWriteSlot(provider, now)) {
            return;
        }
        try {
            int rows = repository.markUnavailable(provider.name(), minutes, reason);
            log.warn("[LlmBot {}] Key marked unavailable for {} minutes ({}); rows updated: {}",
                    provider, minutes, reason, rows);
        } catch (RuntimeException e) {
            log.warn("[LlmBot {}] Could not record that the key is unavailable: {}",
                    provider, e.getClass().getSimpleName());
        }
    }

    private synchronized boolean takeWriteSlot(BotBrainType provider, long now) {
        Long next = nextWriteAtMillis.get(provider);
        if (next != null && now < next) {
            return false;
        }
        nextWriteAtMillis.put(provider, now + MIN_WRITE_INTERVAL_MILLIS);
        return true;
    }

    private static String truncate(String message, int maxLength) {
        if (message == null || message.isBlank()) {
            return "no message";
        }
        String oneLine = message.replaceAll("\\p{Cntrl}+", " ").trim();
        return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength);
    }
}
