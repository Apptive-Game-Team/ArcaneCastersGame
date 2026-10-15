package com.wordonline.server.bot.llm;

/**
 * One provider call that produced no usable answer.
 *
 * <p>{@code status} is the HTTP status, or 0 when the call never got one (missing key, timeout,
 * connection error, unparseable body). {@code retryAfterMillis} is the 429 Retry-After, or -1.
 * The message must never contain the API key.
 */
public class LlmCallFailure extends Exception {

    private final int status;
    private final long retryAfterMillis;

    public LlmCallFailure(String message) {
        this(message, 0, -1);
    }

    public LlmCallFailure(String message, int status, long retryAfterMillis) {
        super(message, null, false, false);
        this.status = status;
        this.retryAfterMillis = retryAfterMillis;
    }

    public int status() {
        return status;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
