package com.wordonline.server.alert;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wordonline.server.alert.config.AlertProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Raises a Discord alert while game loops are running below their target frame rate.
 *
 * <p>The frame rates come from the watchdog sweep that already walks every session, so this
 * class holds no schedule of its own; it is called with whatever that sweep saw and decides
 * what is worth saying.
 *
 * <p>What it says is one message per episode, not one per session per sweep. A server past its
 * capacity has dozens of slow sessions and the sweep repeats every few seconds, so the naive
 * form is a few hundred webhook posts a minute - past Discord's rate limit and useless to read.
 * An episode opens with a summary, repeats at most once per {@link AlertProperties#cooldown()}
 * while it lasts, and closes with a recovery line.
 */
@Slf4j
@Component
public class FrameRateAlerter {

    private static final int NAMED_SESSIONS = 3;

    private final AlertProperties alertProperties;
    private final DiscordNotifier discordNotifier;
    private final Clock clock;

    private Instant lastAlertAt;
    private boolean degraded;

    @Autowired
    public FrameRateAlerter(AlertProperties alertProperties, DiscordNotifier discordNotifier) {
        this(alertProperties, discordNotifier, Clock.systemUTC());
    }

    FrameRateAlerter(AlertProperties alertProperties, DiscordNotifier discordNotifier, Clock clock) {
        this.alertProperties = alertProperties;
        this.discordNotifier = discordNotifier;
        this.clock = clock;
    }

    /**
     * @param frameRates one entry per running session, in whatever order the sweep found them
     */
    public synchronized void report(List<SessionFrameRate> frameRates) {
        if (!discordNotifier.isEnabled()) {
            return;
        }

        List<SessionFrameRate> slow = frameRates.stream()
                .filter(rate -> rate.fps() < alertProperties.fpsThresholdRatio() * rate.targetFps())
                .sorted(Comparator.comparingDouble(rate -> rate.fps() / rate.targetFps()))
                .toList();

        if (slow.isEmpty()) {
            if (degraded) {
                degraded = false;
                lastAlertAt = null;
                discordNotifier.send(String.format(
                        "Game loops recovered: all %d session(s) are back above %.0f%% of their tick rate.",
                        frameRates.size(), alertProperties.fpsThresholdRatio() * 100));
            }
            return;
        }

        Instant now = clock.instant();
        if (degraded && lastAlertAt != null
                && now.isBefore(lastAlertAt.plus(alertProperties.cooldown()))) {
            return;
        }

        degraded = true;
        lastAlertAt = now;
        discordNotifier.send(summarize(slow, frameRates.size()));
    }

    private String summarize(List<SessionFrameRate> slow, int total) {
        String worst = slow.stream()
                .limit(NAMED_SESSIONS)
                .map(rate -> String.format("%s %.1f/%d fps", rate.sessionId(), rate.fps(), rate.targetFps()))
                .collect(Collectors.joining(", "));

        return String.format(
                "Game loops below %.0f%% of their tick rate: %d of %d session(s). Slowest: %s%s",
                alertProperties.fpsThresholdRatio() * 100,
                slow.size(),
                total,
                worst,
                slow.size() > NAMED_SESSIONS ? ", ..." : "");
    }

    /**
     * @param fps frames per second of the last frame, or of the time spent inside the current
     *            one when that is already longer - a loop that stopped ticking reads as slow
     *            rather than as whatever it managed before it stopped
     * @param targetFps the session's tick rate, which the threshold is a share of
     */
    public record SessionFrameRate(String sessionId, double fps, int targetFps) {
    }
}
