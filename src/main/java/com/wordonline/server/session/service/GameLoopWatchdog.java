package com.wordonline.server.session.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wordonline.server.alert.FrameRateAlerter;
import com.wordonline.server.alert.FrameRateAlerter.SessionFrameRate;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.session.config.WatchdogProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects game loops that stopped ticking and reaps their sessions.
 *
 * <p>Every in-loop safeguard (the match timer, the crash handler) runs on the loop thread
 * itself, so a deadlocked or runaway frame disables all of them at once while the session
 * keeps reporting itself as alive. This is the one check that runs on another thread: it
 * watches {@link GameLoop#getLastFrameEndMillis()}, the only signal that stops moving when
 * the loop does, and hands stalled sessions to
 * {@link SessionService#reapStuckSession(SessionObject, String)} together with a snapshot
 * of where the loop thread was blocked.
 *
 * <p>The same pass reads each surviving loop's frame rate and hands it to
 * {@link FrameRateAlerter}. A loop that is merely too slow to play is invisible to the reaping
 * threshold - it keeps completing frames - and nothing else on the server looks at every
 * session on a timer, so the alert is raised from here rather than from a second sweep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameLoopWatchdog {

    private final SessionService sessionService;
    private final WatchdogProperties watchdogProperties;
    private final FrameRateAlerter frameRateAlerter;

    @Scheduled(fixedDelayString = "${watchdog.check-interval-ms:5000}")
    public void reapStuckSessions() {
        long thresholdMillis = watchdogProperties.stuckThreshold().toMillis();
        List<SessionFrameRate> frameRates = new ArrayList<>();

        for (SessionObject sessionObject : sessionService.getSessionObjects()) {
            GameLoop loop = sessionObject.getGameLoop();
            if (loop == null || !loop.is_running()) {
                continue;
            }
            long stallMillis = System.currentTimeMillis() - loop.getLastFrameEndMillis();
            if (stallMillis < thresholdMillis) {
                frameRate(loop, stallMillis)
                        .ifPresent(fps -> frameRates.add(new SessionFrameRate(sessionObject.getSessionId(), fps,
                                loop.getGameContext().getTickRate())));
                continue;
            }

            String stackTrace = formatStackTrace(loop.captureLoopThreadStackTrace());
            log.error("[Watchdog] Loop stalled for {} ms; sessionId: {}, frame: {}, loop thread at:\n{}",
                    stallMillis,
                    sessionObject.getSessionId(),
                    loop.getGameContext().getFrameNum(),
                    stackTrace.isEmpty() ? "(thread gone)" : stackTrace);

            String endDetail = "stalled for " + stallMillis + " ms at frame "
                    + loop.getGameContext().getFrameNum()
                    + (stackTrace.isEmpty() ? "" : "\n" + stackTrace);
            sessionService.reapStuckSession(sessionObject, endDetail);
        }

        frameRateAlerter.report(frameRates);
    }

    /**
     * The rate of the last completed frame, or of the frame currently in flight once that one
     * has run longer. Without the second half a loop that is falling behind keeps reporting the
     * comfortable rate of the frame before it started struggling.
     */
    private Optional<Double> frameRate(GameLoop loop, long stallMillis) {
        double frameSeconds = Math.max(loop.getGameContext().getDeltaTime(), stallMillis / 1000.0);
        return frameSeconds > 0 ? Optional.of(1.0 / frameSeconds) : Optional.empty();
    }

    private String formatStackTrace(StackTraceElement[] stackTrace) {
        return Arrays.stream(stackTrace)
                .map(element -> "at " + element)
                .collect(Collectors.joining("\n"));
    }
}
