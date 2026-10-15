package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LlmKeyCipherTest {

    // The shared vector from the cross-module contract; the admin server must pass the same one.
    private static final String VECTOR = "v1:AAECAwQFBgcICQoLNPLn4fKoUrn0jGzk9jqoG8X9GnxnKrgGMmqw9Q==";

    private final LlmKeyCipher cipher = new LlmKeyCipher("test-secret");

    @Test
    void decryptsTheContractTestVector() {
        assertThat(cipher.decrypt("JEV", VECTOR)).isEqualTo("sk-test-1234");
    }

    @Test
    void encryptsTheContractTestVectorWithItsIv() {
        byte[] iv = new byte[12];
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) i;
        }
        assertThat(cipher.encrypt("JEV", "sk-test-1234", iv)).isEqualTo(VECTOR);
    }

    @Test
    void roundTripsWithARandomIv() {
        String stored = cipher.encrypt("OPENROUTER", "sk-or-v1-abcdef");

        assertThat(stored).startsWith("v1:");
        assertThat(cipher.decrypt("OPENROUTER", stored)).isEqualTo("sk-or-v1-abcdef");
        assertThat(cipher.encrypt("OPENROUTER", "sk-or-v1-abcdef")).isNotEqualTo(stored);
    }

    @Test
    void refusesACiphertextMovedToAnotherProvider() {
        assertThatThrownBy(() -> cipher.decrypt("OPENROUTER", VECTOR))
                .isInstanceOf(LlmKeyCipher.LlmKeyCipherException.class)
                .hasMessageNotContaining("sk-test");
    }

    @Test
    void refusesADifferentSecret() {
        assertThatThrownBy(() -> new LlmKeyCipher("other-secret").decrypt("JEV", VECTOR))
                .isInstanceOf(LlmKeyCipher.LlmKeyCipherException.class)
                .hasMessageContaining("LLM_KEY_SECRET");
    }

    @Test
    void refusesMalformedValues() {
        assertThatThrownBy(() -> cipher.decrypt("JEV", "sk-test-1234"))
                .isInstanceOf(LlmKeyCipher.LlmKeyCipherException.class);
        assertThatThrownBy(() -> cipher.decrypt("JEV", "v1:@@@"))
                .isInstanceOf(LlmKeyCipher.LlmKeyCipherException.class);
        assertThatThrownBy(() -> cipher.decrypt("JEV", "v1:AAEC"))
                .isInstanceOf(LlmKeyCipher.LlmKeyCipherException.class);
    }

    @Test
    void aBlankSecretIsNotConfiguredAndDecryptsNothing() {
        LlmKeyCipher blank = new LlmKeyCipher("  ");

        assertThat(blank.isConfigured()).isFalse();
        assertThatThrownBy(() -> blank.decrypt("JEV", VECTOR))
                .isInstanceOf(LlmKeyCipher.LlmKeyCipherException.class);
    }
}
