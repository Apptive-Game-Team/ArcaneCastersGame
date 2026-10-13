package com.wordonline.server.local;

/**
 * The {@code matchStats} line: how close the loop came to the tick rate the client asked for. The
 * client raises or lowers the next match's tick rate from {@code p10Fps}.
 */
public record LocalMatchStats(
        String type,
        String sessionId,
        int targetTickRate,
        double meanFps,
        double p10Fps
) {
    public LocalMatchStats(String sessionId, int targetTickRate, double meanFps, double p10Fps) {
        this("matchStats", sessionId, targetTickRate, meanFps, p10Fps);
    }
}
