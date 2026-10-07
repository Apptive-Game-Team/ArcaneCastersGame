package com.wordonline.server.playground;

import java.time.Instant;

public record PlaygroundInfo(String sessionId, String server, String webSocketUrl,
                             long ownerId, Instant expiresAt) {}
