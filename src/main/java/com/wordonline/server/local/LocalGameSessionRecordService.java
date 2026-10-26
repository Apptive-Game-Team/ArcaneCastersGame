package com.wordonline.server.local;

import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.server.config.ServerIdentityProperties;
import com.wordonline.server.server.service.ServerInstanceIdProvider;
import com.wordonline.server.statistic.domain.GameSessionStatus;
import com.wordonline.server.statistic.repository.GameSessionRecordRepository;
import com.wordonline.server.statistic.service.GameSessionRecordService;

/**
 * No session lifecycle rows on a locally hosted server. The rows exist so the shared database can
 * tell a crashed process from a running one; a local process dies with its client, and its
 * database dies with it.
 */
@Service
@Profile("local")
public class LocalGameSessionRecordService extends GameSessionRecordService {

    public LocalGameSessionRecordService(GameSessionRecordRepository repository,
                                         ServerIdentityProperties serverIdentityProperties,
                                         ServerInstanceIdProvider serverInstanceIdProvider,
                                         BuildProperties buildProperties) {
        super(repository, serverIdentityProperties, serverInstanceIdProvider, buildProperties);
    }

    @Override
    public void recordStart(SessionObject sessionObject) {
    }

    @Override
    public void recordEnd(String sessionId, GameSessionStatus status, String endReason,
                          String endDetail, Long statisticGameId) {
    }

    @Override
    public void abandonLeftoverSessions() {
    }
}
