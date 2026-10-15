package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = LlmBotPropertiesScanTest.ScanConfig.class)
@TestPropertySource(properties = {"bot.llm.key-secret=s3cret", "bot.llm.timeout-ms=2500"})
class LlmBotPropertiesScanTest {

    @Autowired
    private LlmBotProperties properties;

    @Test
    void bindsSetKeysAndDefaultsTheRest() {
        assertThat(properties.hasKeySecret()).isTrue();
        assertThat(properties.timeoutMs()).isEqualTo(2500);
        assertThat(properties.minIntervalMs()).isEqualTo(LlmBotProperties.DEFAULT_MIN_INTERVAL_MS);
        assertThat(properties.poolSize()).isEqualTo(LlmBotProperties.DEFAULT_POOL_SIZE);
        assertThat(properties.jevUrl()).isEqualTo("https://jev-ai.pro/api/v1/systemone");
        assertThat(properties.jevDefaultModel()).isEqualTo("jev-latest");
        assertThat(properties.openrouterUrl()).isEqualTo("https://openrouter.ai/api/v1/chat/completions");
        assertThat(properties.openrouterDefaultModel()).isEqualTo("openrouter/auto");
        assertThat(properties.toString()).doesNotContain("s3cret");
    }

    @Configuration
    @ConfigurationPropertiesScan("com.wordonline.server.bot.llm")
    static class ScanConfig {
    }
}
