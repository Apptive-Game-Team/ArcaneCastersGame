package com.wordonline.server.session.util;

import com.wordonline.server.deck.service.DeckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.channel.CompositeFrameChannel;
import com.wordonline.server.game.channel.FrameChannel;
import com.wordonline.server.game.channel.StompFrameChannel;
import com.wordonline.server.game.channel.UdpFrameChannel;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.udp.UdpPeerRegistry;
import com.wordonline.server.udp.UdpServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionObjectFactory {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final DeckService deckService;
    private final ObjectProvider<UdpServer> udpServer;
    private final UdpPeerRegistry udpPeerRegistry;
    private final ObjectMapper objectMapper;

    public SessionObjectFactory(SimpMessagingTemplate simpMessagingTemplate, DeckService deckService,
                                ObjectProvider<UdpServer> udpServer, UdpPeerRegistry udpPeerRegistry,
                                ObjectMapper objectMapper) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.deckService = deckService;
        this.udpServer = udpServer;
        this.udpPeerRegistry = udpPeerRegistry;
        this.objectMapper = objectMapper;
    }

    /** STOMP alone, or STOMP plus UDP when the server listens on UDP. */
    private FrameChannel frameChannel(String sessionId) {
        FrameChannel stomp = new StompFrameChannel(simpMessagingTemplate, StompFrameChannel.frameInfoUrl(sessionId));
        UdpServer server = udpServer.getIfAvailable();
        if (server == null) {
            return stomp;
        }
        return new CompositeFrameChannel(stomp, new UdpFrameChannel(sessionId, udpPeerRegistry, server, objectMapper));
    }

    public SessionObject createSessionObject(SessionDto sessionDto) {
        Long uid1 = sessionDto.uid1();
        Long uid2 = sessionDto.uid2();
        String sessionId = sessionDto.sessionId();

        SessionType sessionType = resolveSessionType(sessionDto, sessionId, uid1, uid2);

        return switch (sessionType) {
            case PVE -> createPveSessionObject(sessionId, uid1, sessionDto.scenarioId());
            case Practice -> createPracticeSessionObject(sessionId, uid1, uid2);
            case PVP -> createPvpSessionObject(sessionId, uid1, uid2,
                    sessionDto.leftDeckCardIds(), sessionDto.rightDeckCardIds());
        };
    }

    private SessionType resolveSessionType(SessionDto sessionDto, String sessionId, Long uid1, Long uid2) {
        SessionType sessionType = sessionDto.sessionType();
        if (sessionType != null) {
            return sessionType;
        }

        boolean isPve = sessionId != null && sessionId.toLowerCase().contains("pve");
        if (isPve) {
            return SessionType.PVE;
        }

        if (uid1 < 0 || uid2 < 0) {
            return SessionType.Practice;
        }

        return SessionType.PVP;
    }

    private SessionObject createPvpSessionObject(String sessionId, long uid1, long uid2,
                                                  List<Long> leftDeckCardIds,
                                                  List<Long> rightDeckCardIds) {
        List<Long> leftCards = deckService.getParticipantCards(uid1, leftDeckCardIds);
        List<Long> rightCards = deckService.getParticipantCards(uid2, rightDeckCardIds);
        return new SessionObject(sessionId, uid1, uid2, frameChannel(sessionId), leftCards, rightCards, SessionType.PVP, null);
    }

    private SessionObject createPracticeSessionObject(String sessionId, long uid1, long uid2) {
        List<Long> leftCards = deckService.getParticipantCards(uid1);
        List<Long> rightCards = deckService.getParticipantCards(uid2);
        return new SessionObject(sessionId, uid1, uid2, frameChannel(sessionId), leftCards, rightCards, SessionType.Practice, null);
    }

    private SessionObject createPveSessionObject(String sessionId, long uid1, Long scenarioId) {
        List<Long> leftCards = uid1 >= 0 ? deckService.getSelectedCards(uid1) : List.of();
        return new SessionObject(sessionId, uid1, -1, frameChannel(sessionId), leftCards, List.of(), SessionType.PVE, scenarioId);
    }
}
