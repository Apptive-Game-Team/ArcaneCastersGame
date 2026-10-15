package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.wordonline.server.bot.domain.BotBrainType;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.bot.RuleDecisionSource;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;

class BotDecisionSourceFactoryTest {

    private final DatabaseMagicParser magicParser = mock(DatabaseMagicParser.class);
    private final BotCounterEvaluator counterEvaluator = mock(BotCounterEvaluator.class);

    @Test
    void ruleBrainTypeGetsTheRuleBrain() {
        assertThat(BotDecisionSourceFactory.ruleOnly().create(persona(BotBrainType.RULE), magicParser, counterEvaluator, 0.5))
                .isInstanceOf(RuleDecisionSource.class);
    }

    @Test
    void withoutTheSecretAnLlmPersonaPlaysAsRule() {
        LlmApiKeyProvider keys = new LlmApiKeyProvider(mock(LlmApiKeyRepository.class), new LlmKeyCipher(""),
                mock(LlmKeyUnavailabilityRecorder.class));
        BotDecisionSourceFactory factory = new BotDecisionSourceFactory(LlmBotProperties.defaults(), keys,
                mock(LlmKeyUnavailabilityRecorder.class));

        assertThat(factory.create(persona(BotBrainType.JEV), magicParser, counterEvaluator, 0.5))
                .isInstanceOf(RuleDecisionSource.class);
        assertThat(factory.create(persona(BotBrainType.OPENROUTER), magicParser, counterEvaluator, 0.5))
                .isInstanceOf(RuleDecisionSource.class);
        factory.shutdown();
    }

    @Test
    void withTheSecretAnLlmPersonaGetsTheLlmSource() {
        LlmApiKeyProvider keys = new LlmApiKeyProvider(mock(LlmApiKeyRepository.class), new LlmKeyCipher("secret"),
                mock(LlmKeyUnavailabilityRecorder.class));
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        BotDecisionSourceFactory factory = new BotDecisionSourceFactory(LlmBotProperties.defaults(), keys,
                mock(LlmKeyUnavailabilityRecorder.class),
                (uri, headers, body) -> new LlmHttpClient.Response(500, "", null), executor);

        assertThat(factory.create(persona(BotBrainType.OPENROUTER), magicParser, counterEvaluator, 0.5))
                .isInstanceOf(LlmDecisionSource.class);
        assertThat(factory.create(persona(BotBrainType.JEV), magicParser, counterEvaluator, 0.5))
                .as("jev plays in stance mode by default")
                .isInstanceOf(StanceDecisionSource.class);
        factory.shutdown();
    }

    private static BotPersona persona(BotBrainType brainType) {
        return new BotPersona(-7, "Bot", BotTier.BEGINNER, 250, 8, 0.25, true, false,
                BotTemperament.WARM, brainType, null);
    }
}
