package com.wordonline.server.bot.llm;

import java.util.function.LongSupplier;

import com.wordonline.server.bot.domain.BotBrainType;

import lombok.extern.slf4j.Slf4j;

/**
 * Failure count, back-off window and WARN rate limit for one provider, shared by every bot that
 * uses it: a bad key or an empty balance is the provider account's problem, not one bot's.
 *
 * <p>After {@link #FAILURES_BEFORE_BACKOFF} consecutive failures no bot calls the provider for
 * {@link #BACKOFF_MILLIS}; one more failure after the window opens it again. A 429 with
 * Retry-After backs off for that long immediately. Any success resets the count.
 */
@Slf4j
public final class LlmProviderHealth {

    public static final int FAILURES_BEFORE_BACKOFF = 3;
    public static final long BACKOFF_MILLIS = 30_000;
    public static final long WARN_INTERVAL_MILLIS = 60_000;

    private final BotBrainType provider;
    private final LongSupplier clock;

    private int consecutiveFailures;
    private long backoffUntilMillis = Long.MIN_VALUE;
    private long nextWarnAtMillis = Long.MIN_VALUE;

    public LlmProviderHealth(BotBrainType provider, LongSupplier clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public long now() {
        return clock.getAsLong();
    }

    public synchronized boolean isBackingOff() {
        return now() < backoffUntilMillis;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
    }

    public synchronized void recordFailure(LlmCallFailure failure) {
        long now = now();
        consecutiveFailures++;
        long until = backoffUntilMillis;
        if (consecutiveFailures >= FAILURES_BEFORE_BACKOFF) {
            until = Math.max(until, now + BACKOFF_MILLIS);
        }
        if (failure.status() == 429 && failure.retryAfterMillis() > 0) {
            until = Math.max(until, now + failure.retryAfterMillis());
        }
        backoffUntilMillis = until;
        warn(now, failure);
    }

    synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    // At most one line a minute per provider. Only the failure's own message is logged; it never
    // holds the key, and the request with its Authorization header is never logged at all.
    private void warn(long now, LlmCallFailure failure) {
        if (now < nextWarnAtMillis) {
            return;
        }
        nextWarnAtMillis = now + WARN_INTERVAL_MILLIS;
        String hint = switch (failure.status()) {
            case 401 -> " - the stored API key is invalid";
            case 402 -> " - the provider account balance is empty";
            case 429 -> " - rate limited";
            default -> "";
        };
        String backoff = now < backoffUntilMillis
                ? "; backing off for " + (backoffUntilMillis - now) / 1000 + " s"
                : "";
        log.warn("[LlmBot {}] Call failed ({} in a row, status {}): {}{}{}; playing with the rule brain",
                provider, consecutiveFailures, failure.status(), failure.getMessage(), hint, backoff);
    }

    /** "120" seconds or an HTTP date; only the seconds form is honoured, a date falls back to the default window. */
    public static long parseRetryAfterMillis(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return -1;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return seconds <= 0 ? -1 : Math.min(seconds, 3_600) * 1_000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
