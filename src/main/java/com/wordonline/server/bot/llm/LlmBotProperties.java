package com.wordonline.server.bot.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for bot personas whose brain is an LLM provider.
 *
 * <p>{@code keySecret} is the {@code LLM_KEY_SECRET} that the admin server encrypts the provider
 * API keys with. Blank means no key can be read, and every LLM persona plays with the rule brain.
 * {@code unavailableMinutes} is how long a key stays unavailable after HTTP 402 or 403.
 * {@code minIntervalMs} spaces one bot's OpenRouter requests; {@code jevMinIntervalMs} spaces its
 * jev requests, which answer in about 0.2 seconds and so can be asked far more often.
 * {@code jevMode} is {@code PLAY} (jev picks each cast) or {@code STANCE} (jev picks a plan and code
 * casts under it, {@link StanceDecisionSource}).
 */
@ConfigurationProperties(prefix = "bot.llm")
public record LlmBotProperties(
        String keySecret,
        Integer timeoutMs,
        Integer minIntervalMs,
        Integer poolSize,
        String jevUrl,
        String jevDefaultModel,
        String openrouterUrl,
        String openrouterDefaultModel,
        Integer unavailableMinutes,
        Integer jevMinIntervalMs,
        String jevMode
) {
    public static final String JEV_MODE_PLAY = "PLAY";
    public static final String JEV_MODE_STANCE = "STANCE";
    public static final int DEFAULT_TIMEOUT_MS = 4_000;
    public static final int DEFAULT_MIN_INTERVAL_MS = 1_500;
    public static final int DEFAULT_JEV_MIN_INTERVAL_MS = 500;
    public static final int DEFAULT_POOL_SIZE = 4;
    public static final int DEFAULT_UNAVAILABLE_MINUTES = 60;
    public static final String DEFAULT_JEV_URL = "https://api.typesafe.ai/v1/systemone";
    public static final String DEFAULT_JEV_MODEL = "jev-latest";
    public static final String DEFAULT_OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    public static final String DEFAULT_OPENROUTER_MODEL = "openrouter/auto";

    public LlmBotProperties {
        keySecret = keySecret == null ? "" : keySecret;
        timeoutMs = timeoutMs == null || timeoutMs < 1 ? DEFAULT_TIMEOUT_MS : timeoutMs;
        minIntervalMs = minIntervalMs == null || minIntervalMs < 0 ? DEFAULT_MIN_INTERVAL_MS : minIntervalMs;
        poolSize = poolSize == null || poolSize < 1 ? DEFAULT_POOL_SIZE : poolSize;
        jevUrl = blankTo(jevUrl, DEFAULT_JEV_URL);
        jevDefaultModel = blankTo(jevDefaultModel, DEFAULT_JEV_MODEL);
        openrouterUrl = blankTo(openrouterUrl, DEFAULT_OPENROUTER_URL);
        openrouterDefaultModel = blankTo(openrouterDefaultModel, DEFAULT_OPENROUTER_MODEL);
        unavailableMinutes = unavailableMinutes == null || unavailableMinutes < 1
                ? DEFAULT_UNAVAILABLE_MINUTES : unavailableMinutes;
        jevMinIntervalMs = jevMinIntervalMs == null || jevMinIntervalMs < 0
                ? DEFAULT_JEV_MIN_INTERVAL_MS : jevMinIntervalMs;
        jevMode = JEV_MODE_PLAY.equalsIgnoreCase(blankTo(jevMode, JEV_MODE_STANCE)) ? JEV_MODE_PLAY : JEV_MODE_STANCE;
    }

    public boolean jevStance() {
        return JEV_MODE_STANCE.equals(jevMode);
    }

    /** Every setting at its default and no secret: the LLM brains are off. */
    public static LlmBotProperties defaults() {
        return new LlmBotProperties(null, null, null, null, null, null, null, null, null, null, null);
    }

    public boolean hasKeySecret() {
        return !keySecret.isBlank();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    // The secret must never reach a log line through an accidental toString.
    @Override
    public String toString() {
        return "LlmBotProperties[keySecret=" + (hasKeySecret() ? "<set>" : "<blank>")
                + ", timeoutMs=" + timeoutMs + ", minIntervalMs=" + minIntervalMs
                + ", poolSize=" + poolSize + ", jevUrl=" + jevUrl + ", jevDefaultModel=" + jevDefaultModel
                + ", openrouterUrl=" + openrouterUrl + ", openrouterDefaultModel=" + openrouterDefaultModel
                + ", unavailableMinutes=" + unavailableMinutes + ", jevMinIntervalMs=" + jevMinIntervalMs
                + ", jevMode=" + jevMode + "]";
    }
}
