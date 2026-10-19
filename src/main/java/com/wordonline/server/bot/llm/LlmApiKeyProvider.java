package com.wordonline.server.bot.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wordonline.server.bot.domain.BotBrainType;

import lombok.extern.slf4j.Slf4j;

/**
 * Hands out decrypted provider API keys, cached for {@link #CACHE_MILLIS} so a match full of LLM
 * bots does not read the database on every request. A missing or undecryptable key is cached too,
 * for the same time, so a broken row costs one read a minute rather than one per request. A key
 * whose row is marked unavailable counts as missing, so the persona plays with the rule brain
 * without calling the provider. A key that cannot be decrypted is also recorded as unavailable.
 *
 * <p>Called from the LLM request pool, never from the bot executor or the game loop.
 */
@Slf4j
@Component
public class LlmApiKeyProvider {

    static final long CACHE_MILLIS = 60_000;

    private final LlmApiKeyRepository repository;
    private final LlmKeyCipher cipher;
    private final LlmKeyUnavailabilityRecorder recorder;
    private final LongSupplier clock;
    private final Map<BotBrainType, CachedKey> cache = new ConcurrentHashMap<>();

    @Autowired
    public LlmApiKeyProvider(LlmApiKeyRepository repository, LlmKeyCipher cipher,
                             LlmKeyUnavailabilityRecorder recorder) {
        this(repository, cipher, recorder, System::currentTimeMillis);
    }

    LlmApiKeyProvider(LlmApiKeyRepository repository, LlmKeyCipher cipher,
                      LlmKeyUnavailabilityRecorder recorder, LongSupplier clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.recorder = recorder;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return cipher.isConfigured();
    }

    /** The plaintext key for the provider, or a failure that names what is wrong without the key. */
    public String requireKey(BotBrainType provider) throws LlmCallFailure {
        long now = clock.getAsLong();
        CachedKey cached = cache.get(provider);
        if (cached == null || now >= cached.expiresAtMillis()) {
            cached = load(provider, now);
            cache.put(provider, cached);
        }
        if (cached.key() == null) {
            throw new LlmCallFailure(cached.problem());
        }
        return cached.key();
    }

    /** Forgets the cached key, so the next request reads the row again and sees what was just recorded. */
    public void invalidate(BotBrainType provider) {
        cache.remove(provider);
    }

    private CachedKey load(BotBrainType provider, long now) {
        long expiresAt = now + CACHE_MILLIS;
        if (!cipher.isConfigured()) {
            return new CachedKey(null, "LLM_KEY_SECRET is blank.", expiresAt);
        }
        try {
            return repository.findStoredKey(provider.name())
                    .map(stored -> stored.unavailable()
                            ? new CachedKey(null, "The " + provider + " key is marked unavailable: "
                                    + stored.unavailableReason(), expiresAt)
                            : new CachedKey(cipher.decrypt(provider.name(), stored.encryptedKey()), null, expiresAt))
                    .orElseGet(() -> new CachedKey(null, "No API key is stored for " + provider + ".", expiresAt));
        } catch (LlmKeyCipher.LlmKeyCipherException e) {
            recorder.recordDecryptFailure(provider);
            return new CachedKey(null, e.getMessage(), expiresAt);
        } catch (RuntimeException e) {
            // A database error: do not cache it as long as a real answer, retry on the next request.
            log.debug("[LlmApiKeyProvider] Reading the {} key failed", provider, e);
            return new CachedKey(null, "Reading the " + provider + " key failed: " + e.getClass().getSimpleName(), now);
        }
    }

    private record CachedKey(String key, String problem, long expiresAtMillis) {
        @Override
        public String toString() {
            return "CachedKey[key=" + (key == null ? "<none>" : "<set>") + ", problem=" + problem + "]";
        }
    }
}
