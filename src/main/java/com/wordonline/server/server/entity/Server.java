package com.wordonline.server.server.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "servers")
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String protocol;
    private String domain;
    private Integer port;

    @Enumerated(EnumType.STRING)
    private ServerType type;

    @Setter
    @Enumerated(EnumType.STRING)
    private ServerState state;

    @Setter
    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Setter
    @Column(name = "session_count", nullable = false)
    private Integer sessionCount;

    @Setter
    @Column(name = "max_sessions", nullable = false)
    private Integer maxSessions;

    /**
     * Boot generation of the process that last wrote this row. Nullable: rows written before
     * the column existed, and rows written by servers that predate this field, carry no id.
     */
    @Setter
    @Column(name = "instance_id", length = 64)
    private String instanceId;

    /**
     * Internal network base URL (scheme, host, port) other services on the same docker network
     * can reach this process at, e.g. {@code http://ac-game-blue:8080}. Never has a trailing
     * slash. Null when this process has not reported one; readers fall back to the public
     * {@code protocol://domain:port} address.
     */
    @Setter
    @Column(name = "internal_base_url", length = 255)
    private String internalBaseUrl;

    /**
     * Admin override for the bot scheduler's target session count. Written by the admin server;
     * mapped read-only here so the whole-row heartbeat save cannot write back a stale value and
     * undo an admin change made between read and save. {@code null} means no override; the
     * scheduler falls back to {@code bot.auto-match.target-games}.
     */
    @Column(name = "target_bot_sessions", insertable = false, updatable = false)
    private Integer targetBotSessions;

    public String getUrl() {
        return String.format("%s://%s:%d", protocol, domain, port);
    }

    public Server(String protocol, String domain, int port, ServerType serverType, ServerState state) {
        this(null, protocol, domain, port, serverType, state, null, 0, 100, null, null, null);
    }
}
