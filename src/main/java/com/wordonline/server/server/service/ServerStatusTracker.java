package com.wordonline.server.server.service;

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.session.service.SessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// A locally hosted server is not one of the shared servers and never publishes a servers row.
@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class ServerStatusTracker {

    private final ConfigurableApplicationContext context;
    private final ServerStatusService serverStatusService;
    private final SessionService sessionService;

    private final Subscriber<Integer> onSessionNumChange = new Subscriber<>() {
        private Subscription subscription;
        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1); // 구독 시작
        }

        // No database write here. Every session start, end and reap raises this event, so
        // writing the row per event cost two connection acquisitions each while bot self-play
        // was running. The @Scheduled heartbeat below republishes the count anyway, and nothing
        // downstream needs it sooner: the lobby picks servers from its own health probes and
        // never reads session_count, and the admin dashboard already polls a value that the
        // heartbeat refreshes. The drain check stays - it is what lets a DRAINING server exit
        // once it empties.
        @Override
        public void onNext(Integer item) {
            subscription.request(1);
            if (item == 0) {
                onSessionZero();
            }
            log.debug("[Server State] Session number changed: {}", item);
        }

        @Override
        public void onError(Throwable throwable) {
            log.error("Error occurred in session number change subscriber", throwable);
        }

        @Override
        public void onComplete() {
        }
    };

    private void onSessionZero() {
        log.info("[Server State] num of game session is zero");
        if (serverStatusService.getCurrentState().equals(ServerState.DRAINING)) {
            log.info("[KILLING] kill server");
            SpringApplication.exit(context, () -> 0);
            System.exit(0);
        }
    }

    @PostConstruct
    public void onStart() {
        serverStatusService.setServerStatus(ServerState.ACTIVE);
        sessionService.subscribeSessionNumChange(onSessionNumChange);
        publishHeartbeat((int) sessionService.getActiveSessions());
    }

    @Scheduled(fixedDelayString = "${server.heartbeat-interval-ms}")
    public void heartbeat() {
        publishHeartbeat((int) sessionService.getActiveSessions());
    }

    @PreDestroy
    public void onDestroy() {
        serverStatusService.setServerStatus(ServerState.INACTIVE);
    }

    private void publishHeartbeat(int sessionCount) {
        try {
            serverStatusService.publishHeartbeat(sessionCount);
        } catch (RuntimeException exception) {
            log.error("[Server State] Failed to publish heartbeat; sessionCount: {}", sessionCount, exception);
        }
    }
}
