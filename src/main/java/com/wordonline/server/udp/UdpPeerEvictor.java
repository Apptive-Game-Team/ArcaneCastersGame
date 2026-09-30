package com.wordonline.server.udp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * A client that subscribes to its frame topic over STOMP has fallen back from UDP. The UDP peer
 * would otherwise keep receiving the frames until it idles out, and the STOMP subscriber none.
 */
@Component
public class UdpPeerEvictor {

    private static final Pattern FRAME_TOPIC = Pattern.compile("^/game/([^/]+)/frameInfos/(\\d+)$");

    private final UdpPeerRegistry registry;

    public UdpPeerEvictor(UdpPeerRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        String destination = StompHeaderAccessor.wrap(event.getMessage()).getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = FRAME_TOPIC.matcher(destination);
        if (matcher.matches()) {
            registry.removeUser(matcher.group(1), Long.parseLong(matcher.group(2)));
        }
    }
}
