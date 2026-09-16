package com.wordonline.server.lobby.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wordonline.server.lobby.config.LobbyProperties;

/**
 * Supplies the service-to-service bearer token used for lobby calls.
 *
 * <p>This server only verifies JWTs against account's JSON Web Key Set and therefore cannot
 * mint a JWT of its own, so it mirrors the lobby's outbound convention: a token issued
 * elsewhere is dropped on disk and read from a configured path. Nothing here ever logs the
 * token value.
 *
 * <p>The token is read lazily and cached only on success, so a token file that is mounted
 * after boot still gets picked up without a restart. Swapping the credential mechanism
 * (mTLS, shared secret header, minted JWT) is a change to this class alone.
 */
@Service
public class LobbyServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(LobbyServiceTokenProvider.class);

    private final LobbyProperties lobbyProperties;

    private volatile String cachedToken;

    public LobbyServiceTokenProvider(LobbyProperties lobbyProperties) {
        this.lobbyProperties = lobbyProperties;
    }

    /**
     * @return the bearer token, or empty when none is configured or readable. Callers must
     *         treat empty as "send unauthenticated and let the lobby decide", never as an error.
     */
    public Optional<String> getToken() {
        String token = cachedToken;
        if (token != null) {
            return Optional.of(token);
        }

        String tokenPath = lobbyProperties.serviceTokenPath();
        if (tokenPath == null || tokenPath.isBlank()) {
            log.warn("[Lobby] Service token path is not configured; lobby calls go out unauthenticated");
            return Optional.empty();
        }

        return Optional.ofNullable(readToken(tokenPath));
    }

    private String readToken(String tokenPath) {
        try {
            String token = Files.readString(Path.of(tokenPath)).trim();
            if (token.isEmpty()) {
                log.warn("[Lobby] Service token file is empty; path: {}", tokenPath);
                return null;
            }
            cachedToken = token;
            log.info("[Lobby] Service token loaded; path: {}", tokenPath);
            return token;
        } catch (IOException | RuntimeException e) {
            log.warn("[Lobby] Failed to read service token; path: {}", tokenPath, e);
            return null;
        }
    }
}
