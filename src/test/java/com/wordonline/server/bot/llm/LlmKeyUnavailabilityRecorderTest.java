package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wordonline.server.bot.domain.BotBrainType;

class LlmKeyUnavailabilityRecorderTest {

    private static final long THIRTY_DAYS_IN_MINUTES = 43_200;

    private final LlmApiKeyRepository repository = mock(LlmApiKeyRepository.class);
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final LlmKeyUnavailabilityRecorder recorder = new LlmKeyUnavailabilityRecorder(
            repository, new LlmBotProperties(null, null, null, null, null, null, null, null, 90), clock::get);

    @Test
    void a401KeepsTheKeyUnavailableForThirtyDays() {
        recorder.recordFailure(BotBrainType.JEV, new LlmCallFailure("x", 401, -1));

        verify(repository).markUnavailable("JEV", THIRTY_DAYS_IN_MINUTES, "HTTP 401: key rejected");
    }

    @Test
    void a402UsesTheConfiguredMinutes() {
        recorder.recordFailure(BotBrainType.OPENROUTER, new LlmCallFailure("x", 402, -1));

        verify(repository).markUnavailable("OPENROUTER", 90, "HTTP 402: balance empty");
    }

    @Test
    void a403UsesTheConfiguredMinutesAndTheTruncatedProviderMessage() {
        String longMessage = "Key limit exceeded (total limit) " + "x".repeat(300);

        recorder.recordFailure(BotBrainType.OPENROUTER, new LlmCallFailure("x", 403, -1, longMessage));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(repository).markUnavailable(eq("OPENROUTER"), eq(90L), reason.capture());
        assertThat(reason.getValue()).startsWith("HTTP 403: Key limit exceeded (total limit)");
        assertThat(reason.getValue().length()).isEqualTo("HTTP 403: ".length() + 150).isLessThanOrEqualTo(200);
    }

    @Test
    void transientFailuresAreNotRecorded() {
        for (int status : new int[] {0, 400, 404, 429, 500, 502, 503}) {
            recorder.recordFailure(BotBrainType.JEV, new LlmCallFailure("x", status, 5_000));
        }

        verify(repository, never()).markUnavailable(anyString(), anyLong(), anyString());
    }

    @Test
    void anUndecryptableKeyKeepsTheKeyUnavailableForThirtyDays() {
        recorder.recordDecryptFailure(BotBrainType.JEV);

        verify(repository).markUnavailable("JEV", THIRTY_DAYS_IN_MINUTES,
                "stored key cannot be decrypted with this server's LLM_KEY_SECRET");
    }

    @Test
    void writesAtMostOncePerProviderPerMinute() {
        LlmCallFailure rejected = new LlmCallFailure("x", 402, -1);

        recorder.recordFailure(BotBrainType.JEV, rejected);
        recorder.recordFailure(BotBrainType.JEV, rejected);
        clock.addAndGet(59_999);
        recorder.recordFailure(BotBrainType.JEV, rejected);
        recorder.recordFailure(BotBrainType.OPENROUTER, rejected);
        verify(repository, times(1)).markUnavailable(eq("JEV"), anyLong(), anyString());
        verify(repository, times(1)).markUnavailable(eq("OPENROUTER"), anyLong(), anyString());

        clock.addAndGet(1);
        recorder.recordFailure(BotBrainType.JEV, rejected);
        verify(repository, times(2)).markUnavailable(eq("JEV"), anyLong(), anyString());
    }

    @Test
    void aDatabaseFailureDoesNotEscapeAndIsRetriedAfterAMinute() {
        when(repository.markUnavailable(anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("db down"));
        LlmCallFailure rejected = new LlmCallFailure("x", 401, -1);

        recorder.recordFailure(BotBrainType.JEV, rejected);
        recorder.recordFailure(BotBrainType.JEV, rejected);
        verify(repository, times(1)).markUnavailable(anyString(), anyLong(), anyString());

        clock.addAndGet(60_000);
        recorder.recordFailure(BotBrainType.JEV, rejected);
        verify(repository, times(2)).markUnavailable(anyString(), anyLong(), anyString());
    }

    @Test
    void healthHandsOnlyKeyRejectionsToTheListener() {
        java.util.List<Integer> heard = new java.util.ArrayList<>();
        LlmProviderHealth health = new LlmProviderHealth(BotBrainType.JEV, clock::get,
                failure -> heard.add(failure.status()));

        for (int status : new int[] {401, 402, 403, 429, 500, 0}) {
            health.recordFailure(new LlmCallFailure("x", status, -1));
        }

        assertThat(heard).containsExactly(401, 402, 403);
    }

    @Test
    void healthSurvivesAListenerThatThrows() {
        LlmProviderHealth health = new LlmProviderHealth(BotBrainType.JEV, clock::get, failure -> {
            throw new IllegalStateException("boom");
        });

        health.recordFailure(new LlmCallFailure("x", 401, -1));

        assertThat(health.consecutiveFailures()).isEqualTo(1);
    }
}
