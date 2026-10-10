package com.wordonline.server.alert.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operational alerts posted to Discord.
 *
 * <p>The webhook is the same one the log appender is configured with, so a deployment that
 * already sets {@code DISCORD_WEBHOOK_URL} needs no new configuration. An empty URL disables
 * alerting outright, which is what local runs and tests get.
 *
 * <p>{@link #fpsThresholdRatio()} is a share of each session's own tick rate, not a frame rate:
 * sessions run at different rates, so 15 fps is a struggling 20-FPS loop and a dead 60-FPS one.
 *
 * <p>{@link #cooldown()} is what keeps a degraded server from emptying itself into the channel:
 * a box holding fifty sessions below the threshold has one problem, not fifty, and it still has
 * that problem on the next sweep two seconds later.
 */
@ConfigurationProperties(prefix = "alert")
public record AlertProperties(
        Boolean enabled,
        String discordWebhookUrl,
        Double fpsThresholdRatio,
        Duration cooldown) {

    private static final double FALLBACK_FPS_THRESHOLD_RATIO = 0.75;
    private static final Duration FALLBACK_COOLDOWN = Duration.ofMinutes(5);

    public AlertProperties {
        enabled = enabled == null || enabled;
        discordWebhookUrl = discordWebhookUrl == null ? "" : discordWebhookUrl.trim();
        fpsThresholdRatio = fpsThresholdRatio == null ? FALLBACK_FPS_THRESHOLD_RATIO : fpsThresholdRatio;
        cooldown = cooldown == null ? FALLBACK_COOLDOWN : cooldown;
    }

    public boolean active() {
        return enabled && !discordWebhookUrl.isEmpty();
    }
}
