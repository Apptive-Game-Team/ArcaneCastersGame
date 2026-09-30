package com.wordonline.server.game.channel;

import org.springframework.messaging.simp.SimpMessagingTemplate;

/** {@link FrameChannel} over the STOMP simple broker: {@code /game/{sessionId}/frameInfos/{userId}}. */
public class StompFrameChannel implements FrameChannel {

    /** userId 0 is the spectator topic. */
    private static final long SPECTATOR_ID = 0;

    private final SimpMessagingTemplate template;
    private final String userPrefix;
    private final String broadcastDestination;

    public StompFrameChannel(SimpMessagingTemplate template, String frameInfoUrl) {
        this.template = template;
        this.userPrefix = frameInfoUrl + "/";
        this.broadcastDestination = userPrefix + SPECTATOR_ID;
    }

    public static String frameInfoUrl(String sessionId) {
        return String.format("/game/%s/frameInfos", sessionId);
    }

    @Override
    public void send(long userId, Object data) {
        template.convertAndSend(userPrefix + userId, data);
    }

    @Override
    public void broadcast(Object data) {
        template.convertAndSend(broadcastDestination, data);
    }
}
