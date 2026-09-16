package com.wordonline.server.server.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.server.server.config.ServerIdentityProperties;
import com.wordonline.server.server.entity.Server;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.entity.ServerType;
import com.wordonline.server.server.repository.ServerRepository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServerStatusService {

    private final ServerRepository serverRepository;
    private final ServerIdentityProperties serverIdentityProperties;
    private final ServerInstanceIdProvider serverInstanceIdProvider;

    @Getter
    private volatile ServerState currentState = ServerState.ACTIVE;

    // One transaction around the read and the write. publishStatus is private, so the
    // annotation has to sit on the entry points the proxy actually sees; without it the
    // findByDomainAndPort and the @Transactional inside SimpleJpaRepository.save each take
    // their own connection for what is a single row update.
    @Transactional
    public synchronized void setServerStatus(ServerState state) {
        publishStatus(state, state == ServerState.INACTIVE ? 0 : null);
    }

    /**
     * The admin's per-server override for the bot scheduler's target session count, read fresh
     * from this server's own row so a change applies on the next scheduler tick without a
     * restart. Empty when the row is missing or the admin has not set an override.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> findTargetBotSessions() {
        return serverRepository
                .findByDomainAndPort(serverIdentityProperties.domain(), serverIdentityProperties.externalPort())
                .map(Server::getTargetBotSessions);
    }

    @Transactional
    public synchronized void publishHeartbeat(int sessionCount) {
        publishStatus(currentState, sessionCount);
    }

    private void publishStatus(ServerState state, Integer sessionCount) {
        currentState = state;
        String protocol = serverIdentityProperties.protocol();
        String domain = serverIdentityProperties.domain();
        Integer port = serverIdentityProperties.externalPort();

        Server server = serverRepository.findByDomainAndPort(domain, port)
                .orElseGet(() -> new Server(protocol, domain, port, ServerType.GAME, state));
        server.setState(state);
        server.setLastHeartbeatAt(Instant.now());
        if (sessionCount != null) {
            server.setSessionCount(sessionCount);
        }
        server.setMaxSessions(serverIdentityProperties.maxSessions());
        // Every write, boot and heartbeat alike, so the row always names the process that is
        // actually holding the sessions. The lobby reads a stale id as "those sessions are gone".
        server.setInstanceId(serverInstanceIdProvider.getInstanceId());
        // Same reasoning as instanceId above. ServerIdentityProperties already strips a
        // trailing slash; blank (unset) still has to become NULL rather than "".
        String internalBaseUrl = serverIdentityProperties.internalBaseUrl();
        server.setInternalBaseUrl(internalBaseUrl == null || internalBaseUrl.isBlank() ? null : internalBaseUrl);
        serverRepository.save(server);
    }
}
