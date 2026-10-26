package com.wordonline.server.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The only credential a locally hosted server accepts is the token its client sent in the
 * {@code init} line. It is not a JWT; this decoder turns it into the {@link Jwt} the shared
 * authentication path ({@code JwtProvider}) already reads, so HTTP, the WebSocket handshake and
 * STOMP CONNECT all check it the same way.
 *
 * <p>The player behind the token is the left user of the most recently created local session. The
 * client never talks to this server as anyone else, and one client runs one session at a time.
 */
public class LocalTokenJwtDecoder implements JwtDecoder {

    // The authority a local player holds. Deliberately none of the shared servers' role names, so no
    // administrative or server-to-server endpoint opens to the local token.
    static final String LOCAL_PLAYER_SCOPE = "LOCAL_PLAYER";

    private final byte[] token;
    private final AtomicReference<Long> memberId = new AtomicReference<>();

    public LocalTokenJwtDecoder(String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public void setMemberId(long memberId) {
        this.memberId.set(memberId);
    }

    @Override
    public Jwt decode(String presented) {
        if (presented == null
                || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
            throw new BadJwtException("not the local server token");
        }
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue(presented)
                .header("alg", "none")
                .claim("scope", LOCAL_PLAYER_SCOPE)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));
        Long current = memberId.get();
        if (current != null) {
            builder.claim("memberId", current);
        }
        return builder.build();
    }
}
