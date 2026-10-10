package com.wordonline.server.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.alert.FrameRateAlerter.SessionFrameRate;
import com.wordonline.server.alert.config.AlertProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FrameRateAlerterTest {

    private static final Instant START = Instant.parse("2026-08-19T00:00:00Z");

    private final DiscordNotifier discordNotifier = mock(DiscordNotifier.class);
    private final AlertProperties alertProperties =
            new AlertProperties(true, "https://discord.example/webhook", 0.75, Duration.ofMinutes(5));

    private Instant now = START;
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private final FrameRateAlerter alerter =
            new FrameRateAlerter(alertProperties, discordNotifier, clock);

    private void enabled() {
        when(discordNotifier.isEnabled()).thenReturn(true);
    }

    @Test
    void alertsWhenASessionFallsBelowTheThreshold() {
        enabled();

        alerter.report(List.of(new SessionFrameRate("session-1", 9.5, 20),
                new SessionFrameRate("session-2", 20.0, 20)));

        verify(discordNotifier).send(contains("1 of 2 session(s)"));
        verify(discordNotifier).send(contains("session-1 9.5/20 fps"));
    }

    @Test
    void staysQuietWhileEverySessionKeepsUp() {
        enabled();

        alerter.report(List.of(new SessionFrameRate("session-1", 19.9, 20)));

        verify(discordNotifier, never()).send(anyString());
    }

    @Test
    void measuresEachSessionAgainstItsOwnTickRate() {
        enabled();

        alerter.report(List.of(new SessionFrameRate("fast-but-slow", 44.0, 60),
                new SessionFrameRate("slow-but-fine", 16.0, 20)));

        verify(discordNotifier).send(contains("1 of 2 session(s)"));
        verify(discordNotifier).send(contains("fast-but-slow 44.0/60 fps"));
    }

    @Test
    void sendsNothingWhenNoWebhookIsConfigured() {
        when(discordNotifier.isEnabled()).thenReturn(false);

        alerter.report(List.of(new SessionFrameRate("session-1", 1.0, 20)));

        verify(discordNotifier, never()).send(anyString());
    }

    @Test
    void repeatsOnlyAfterTheCooldownHasPassed() {
        enabled();
        List<SessionFrameRate> slow = List.of(new SessionFrameRate("session-1", 4.0, 20));

        alerter.report(slow);
        now = START.plusSeconds(60);
        alerter.report(slow);
        now = START.plusSeconds(120);
        alerter.report(slow);

        verify(discordNotifier).send(anyString());

        now = START.plus(Duration.ofMinutes(5));
        alerter.report(slow);

        verify(discordNotifier, org.mockito.Mockito.times(2)).send(anyString());
    }

    @Test
    void reportsRecoveryOnceTheSessionsCatchUp() {
        enabled();
        alerter.report(List.of(new SessionFrameRate("session-1", 4.0, 20)));

        alerter.report(List.of(new SessionFrameRate("session-1", 20.0, 20)));
        alerter.report(List.of(new SessionFrameRate("session-1", 20.0, 20)));

        verify(discordNotifier).send(contains("recovered"));
    }

    @Test
    void namesAtMostThreeSessionsAndSaysThereAreMore() {
        enabled();

        alerter.report(List.of(
                new SessionFrameRate("session-1", 3.0, 20),
                new SessionFrameRate("session-2", 2.0, 20),
                new SessionFrameRate("session-3", 1.0, 20),
                new SessionFrameRate("session-4", 4.0, 20)));

        verify(discordNotifier).send(contains("session-3 1.0/20 fps, session-2 2.0/20 fps, session-1 3.0/20 fps, ..."));
    }

    @Test
    void anEmptyWebhookUrlDeactivatesTheProperties() {
        assertThat(new AlertProperties(true, "  ", null, null).active()).isFalse();
        assertThat(new AlertProperties(false, "https://discord.example/webhook", null, null).active()).isFalse();
        assertThat(new AlertProperties(null, "https://discord.example/webhook", null, null).active()).isTrue();
    }
}
