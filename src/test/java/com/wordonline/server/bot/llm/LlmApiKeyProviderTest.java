package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
    private final AtomicLong clock = new AtomicLong(0);
    private final LlmApiKeyProvider provider = new LlmApiKeyProvider(repository, cipher, clock::get);

    @Test
    void decryptsAndCachesTheKeyForAMinute() throws Exception {
        when(repository.findEncryptedKey("JEV")).thenReturn(Optional.of(cipher.encrypt("JEV", "sk-jev")));

        assertThat(provider.requireKey(BotBrainType.JEV)).isEqualTo("sk-jev");
        clock.set(LlmApiKeyProvider.CACHE_MILLIS - 1);
        assertThat(provider.requireKey(BotBrainType.JEV)).isEqualTo("sk-jev");
        verify(repository, times(1)).findEncryptedKey("JEV");

        clock.set(LlmApiKeyProvider.CACHE_MILLIS);
        provider.requireKey(BotBrainType.JEV);
        verify(repository, times(2)).findEncryptedKey("JEV");
    }

    @Test
    void aMissingKeyIsAFailureThatNamesTheProvider() {
        when(repository.findEncryptedKey("OPENROUTER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.requireKey(BotBrainType.OPENROUTER))
                .isInstanceOf(LlmCallFailure.class)
                .hasMessageContaining("OPENROUTER");
    }

    @Test
    void aKeyEncryptedForAnotherProviderIsAFailureWithoutTheKey() {
        when(repository.findEncryptedKey("OPENROUTER")).thenReturn(Optional.of(cipher.encrypt("JEV", "sk-jev")));

        assertThatThrownBy(() -> provider.requireKey(BotBrainType.OPENROUTER))
                .isInstanceOf(LlmCallFailure.class)
                .hasMessageNotContaining("sk-jev");
    }
}
