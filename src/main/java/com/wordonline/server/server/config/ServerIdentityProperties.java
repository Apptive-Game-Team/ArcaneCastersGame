package com.wordonline.server.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identity and capacity settings this game server publishes to the shared
 * {@code servers} table so the lobby can route players to it.
 *
 * <p>{@link #internalBaseUrl()} is separate from {@link #protocol()}, {@link #domain()} and
 * {@link #externalPort()}: those three are the public, client-facing address, while
 * {@code internalBaseUrl} is the scheme/host/port other services on the same docker network
 * can reach this process at directly, e.g. {@code http://ac-game-blue:8080}. It carries no
 * trailing slash; blank means this process has not reported one.
 */
@ConfigurationProperties(prefix = "server")
public record ServerIdentityProperties(
        String protocol,
        String domain,
        Integer externalPort,
        Integer maxSessions,
        String internalBaseUrl) {

    public ServerIdentityProperties {
        internalBaseUrl = stripTrailingSlashes(internalBaseUrl);
    }

    private static String stripTrailingSlashes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
