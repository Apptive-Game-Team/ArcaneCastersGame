package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Spring has to be able to build every LLM bean on its own. A bean with a second, test-only
 * constructor and no {@code @Autowired} on the real one fails only at server startup, which the
 * unit tests that call {@code new} never see.
 */
@SpringJUnitConfig(classes = LlmBeanWiringTest.WiringConfig.class)
@TestPropertySource(properties = "bot.llm.key-secret=s3cret")
class LlmBeanWiringTest {

    @MockitoBean
    private LlmApiKeyRepository repository;

    @Autowired
    private BotDecisionSourceFactory factory;

    @Autowired
    private LlmKeyCipher cipher;

    @Test
    void buildsTheFactoryAndItsCollaborators() {
        assertThat(factory).isNotNull();
        assertThat(cipher.decrypt("JEV", cipher.encrypt("JEV", "sk-wiring"))).isEqualTo("sk-wiring");
    }

    @Configuration
    @ConfigurationPropertiesScan("com.wordonline.server.bot.llm")
    @Import({LlmKeyCipher.class, LlmApiKeyProvider.class, BotDecisionSourceFactory.class})
    static class WiringConfig {
    }
}
