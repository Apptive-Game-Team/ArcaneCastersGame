package com.wordonline.server.bot.llm;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wordonline.server.bot.domain.BotBrainType;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.bot.BotDecisionSource;
import com.wordonline.server.game.domain.bot.RuleDecisionSource;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.magic.parser.MagicParser;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Picks the decision source a bot persona's {@code brain_type} asks for.
 *
 * <p>{@code RULE} gets the rule-based {@link BotBrain}. {@code JEV} and {@code OPENROUTER} get an
 * {@link LlmDecisionSource} that falls back to that same rule brain. Without
 * {@code LLM_KEY_SECRET} no key can be read, so every LLM persona gets the rule brain and one WARN
 * says why.
 *
 * <p>Owns the LLM request pool. It is separate from the bot executor on purpose: that pool runs
 * overflow on the game loop thread, and a blocking HTTP call there would stall the match. This one
 * is small, bounded, and drops a request it has no room for; the bot then plays that tick with the
 * rule brain.
 */
@Slf4j
@Component
public class BotDecisionSourceFactory {

    private static final int QUEUE_PER_THREAD = 2;

    private final LlmBotProperties properties;
    private final LlmApiKeyProvider keyProvider;
    private final LlmHttpClient httpClient;
    private final ThreadPoolExecutor executor;
    private final Map<BotBrainType, LlmProviderClient> clients = new EnumMap<>(BotBrainType.class);
    private final Map<BotBrainType, LlmProviderHealth> health = new EnumMap<>(BotBrainType.class);
    private final AtomicBoolean secretWarned = new AtomicBoolean();

    @Autowired
    public BotDecisionSourceFactory(LlmBotProperties properties, LlmApiKeyProvider keyProvider,
                                    LlmKeyUnavailabilityRecorder recorder) {
        this(properties, keyProvider, recorder,
                LlmHttpClient.jdk(Duration.ofMillis(properties.timeoutMs())),
                keyProvider != null && keyProvider.isConfigured() ? newExecutor(properties.poolSize()) : null);
    }

    BotDecisionSourceFactory(LlmBotProperties properties, LlmApiKeyProvider keyProvider,
                             LlmKeyUnavailabilityRecorder recorder,
                             LlmHttpClient httpClient, ThreadPoolExecutor executor) {
        this.properties = properties;
        this.keyProvider = keyProvider;
        this.httpClient = httpClient;
        this.executor = executor;
        clients.put(BotBrainType.JEV, new JevProviderClient(properties.jevUrl(), properties.jevDefaultModel()));
        clients.put(BotBrainType.OPENROUTER,
                new OpenRouterProviderClient(properties.openrouterUrl(), properties.openrouterDefaultModel()));
        for (BotBrainType type : clients.keySet()) {
            health.put(type, new LlmProviderHealth(type, System::currentTimeMillis, failure -> {
                if (recorder != null) {
                    recorder.recordFailure(type, failure);
                }
                if (keyProvider != null) {
                    keyProvider.invalidate(type);
                }
            }));
        }
    }

    /** A factory that only ever hands out the rule brain, for tests and tools without a database. */
    public static BotDecisionSourceFactory ruleOnly() {
        LlmBotProperties defaults = LlmBotProperties.defaults();
        return new BotDecisionSourceFactory(defaults, null, null, (uri, headers, body) -> {
            throw new IllegalStateException("The rule-only factory makes no HTTP calls.");
        }, null);
    }

    public BotDecisionSource create(BotPersona persona,
                                    MagicParser magicParser,
                                    BotCounterEvaluator counterEvaluator,
                                    double opponentNoviceProgress) {
        RuleDecisionSource rule = new RuleDecisionSource(
                new BotBrain(magicParser, counterEvaluator, persona, opponentNoviceProgress));
        BotBrainType type = persona.normalizedBrainType();
        if (!type.usesLlm()) {
            return rule;
        }
        if (executor == null || keyProvider == null || !keyProvider.isConfigured()) {
            if (secretWarned.compareAndSet(false, true)) {
                log.warn("[LlmBot] LLM_KEY_SECRET is blank, so no provider API key can be read; "
                        + "every JEV and OPENROUTER bot persona plays with the rule brain");
            }
            return rule;
        }
        if (!(magicParser instanceof DatabaseMagicParser databaseMagicParser)) {
            return rule;
        }
        LlmProviderClient client = clients.get(type);
        String model = persona.normalizedLlmModel() != null ? persona.normalizedLlmModel() : client.defaultModel();
        log.debug("[LlmBot {}] Persona {} plays with model {}", type, persona.name(), model);
        return new LlmDecisionSource(
                rule,
                client,
                httpClient,
                () -> keyProvider.requireKey(type),
                health.get(type),
                executor,
                new LlmCandidateBuilder(databaseMagicParser::getMagic, counterEvaluator),
                model,
                minIntervalMillis(type));
    }

    /** jev answers in about 0.2 seconds, so it gets its own, shorter spacing between requests. */
    long minIntervalMillis(BotBrainType type) {
        return type == BotBrainType.JEV ? properties.jevMinIntervalMs() : properties.minIntervalMs();
    }

    private static ThreadPoolExecutor newExecutor(int poolSize) {
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(poolSize * QUEUE_PER_THREAD),
                runnable -> {
                    Thread thread = new Thread(runnable, "bot-llm-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        // Idle threads go away, so a server with no LLM bot in play holds none.
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
