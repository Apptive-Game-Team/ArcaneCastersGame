package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.wordonline.server.bot.domain.BotBrainType;

class LlmApiKeyProviderTest {

    private final LlmApiKeyRepository repository = mock(LlmApiKeyRepository.class);
    private final LlmKeyCipher cipher = new LlmKeyCipher("test-secret");
    private final LlmKeyUnavailabilityRecorder recorder = mock(LlmKeyUnavailabilityRecorder.class);
    private final AtomicLong clock = new AtomicLong(0);
    private final LlmApiKeyProvider provider = new LlmApiKeyProvider(repository, cipher, recorder, clock::get);

    @Test
    void decryptsAndCachesTheKeyForAMinute() throws Exception {
        when(repository.findStoredKey("JEV")).thenReturn(stored(cipher.encrypt("JEV", "sk-jev")));

        assertThat(provider.requireKey(BotBrainType.JEV)).isEqualTo("sk-jev");
        clock.set(LlmApiKeyProvider.CACHE_MILLIS - 1);
        assertThat(provider.requireKey(BotBrainType.JEV)).isEqualTo("sk-jev");
        verify(repository, times(1)).findStoredKey("JEV");

        clock.set(LlmApiKeyProvider.CACHE_MILLIS);
        provider.requireKey(BotBrainType.JEV);
        verify(repository, times(2)).findStoredKey("JEV");
    }

    @Test
    void aMissingKeyIsAFailureThatNamesTheProvider() {
        when(repository.findStoredKey("OPENROUTER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.requireKey(BotBrainType.OPENROUTER))
                .isInstanceOf(LlmCallFailure.class)
                .hasMessageContaining("OPENROUTER");
    }

    @Test
    void aKeyEncryptedForAnotherProviderIsAFailureWithoutTheKey() {
        when(repository.findStoredKey("OPENROUTER")).thenReturn(stored(cipher.encrypt("JEV", "sk-jev")));

        assertThatThrownBy(() -> provider.requireKey(BotBrainType.OPENROUTER))
                .isInstanceOf(LlmCallFailure.class)
                .hasMessageNotContaining("sk-jev");
    }

    @Test
    void aKeyMarkedUnavailableCountsAsMissingAndIsNotDecrypted() {
        when(repository.findStoredKey("JEV")).thenReturn(Optional.of(
                new LlmApiKeyRepository.StoredKey("not-even-ciphertext", true, "HTTP 402: balance empty")));

        assertThatThrownBy(() -> provider.requireKey(BotBrainType.JEV))
                .isInstanceOf(LlmCallFailure.class)
                .hasMessageContaining("unavailable")
                .hasMessageContaining("HTTP 402");
        verify(recorder, never()).recordDecryptFailure(any());
    }

    @Test
    void invalidateMakesTheNextRequestReadTheRowAgain() throws Exception {
        when(repository.findStoredKey("JEV")).thenReturn(stored(cipher.encrypt("JEV", "sk-jev")));
        provider.requireKey(BotBrainType.JEV);

        provider.invalidate(BotBrainType.JEV);
        when(repository.findStoredKey("JEV")).thenReturn(Optional.of(
                new LlmApiKeyRepository.StoredKey("x", true, "HTTP 401: key rejected")));

        assertThatThrownBy(() -> provider.requireKey(BotBrainType.JEV)).isInstanceOf(LlmCallFailure.class);
    }

    @Test
    void anUndecryptableKeyIsRecordedAsUnavailable() {
        when(repository.findStoredKey("JEV")).thenReturn(stored("v1:garbage"));

        assertThatThrownBy(() -> provider.requireKey(BotBrainType.JEV)).isInstanceOf(LlmCallFailure.class);
        verify(recorder).recordDecryptFailure(BotBrainType.JEV);
    }

    private static Optional<LlmApiKeyRepository.StoredKey> stored(String encrypted) {
        return Optional.of(new LlmApiKeyRepository.StoredKey(encrypted, false, null));
    }
}
