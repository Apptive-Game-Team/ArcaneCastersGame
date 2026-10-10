package com.wordonline.server.session.util;

import com.wordonline.server.deck.service.DeckService;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.map.GameMap;
import com.wordonline.server.game.domain.map.GameMapSelector;
import com.wordonline.server.session.dto.SessionDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionObjectFactory {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final DeckService deckService;
    private final GameMapSelector gameMapSelector;

    public SessionObjectFactory(SimpMessagingTemplate simpMessagingTemplate, DeckService deckService,
                                GameMapSelector gameMapSelector) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.deckService = deckService;
        this.gameMapSelector = gameMapSelector;
    }

    public SessionObject createSessionObject(SessionDto sessionDto) {
        Long uid1 = sessionDto.uid1();
        Long uid2 = sessionDto.uid2();
        String sessionId = sessionDto.sessionId();

        SessionType sessionType = resolveSessionType(sessionDto, sessionId, uid1, uid2);

        // The map is decided here, once, and stored on the session. The creation response and the
        // loop both read it from the session, so neither can disagree with the other.
        GameMap map = gameMapSelector.choose(sessionType, sessionDto.scenarioId());

        return switch (sessionType) {
            case PVE -> createPveSessionObject(sessionId, uid1, sessionDto.scenarioId(), map);
            case Playground -> {
                if (!sessionId.startsWith("playground-") || uid1 <= 0 || uid2 != -1)
                    throw new IllegalArgumentException("Invalid playground participants.");
                yield new SessionObject(sessionId, uid1, -1, simpMessagingTemplate,
                        List.of(), List.of(), SessionType.Playground, null, map);
            }
            case Practice -> createPracticeSessionObject(sessionId, uid1, uid2, map);
            case PVP -> createPvpSessionObject(sessionId, uid1, uid2,
                    sessionDto.leftDeckCardIds(), sessionDto.rightDeckCardIds(), map);
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
                                                  List<Long> rightDeckCardIds,
                                                  GameMap map) {
        List<Long> leftCards = deckService.getParticipantCards(uid1, leftDeckCardIds);
        List<Long> rightCards = deckService.getParticipantCards(uid2, rightDeckCardIds);
        return new SessionObject(sessionId, uid1, uid2, simpMessagingTemplate, leftCards, rightCards, SessionType.PVP, null, map);
    }

    private SessionObject createPracticeSessionObject(String sessionId, long uid1, long uid2, GameMap map) {
        List<Long> leftCards = deckService.getParticipantCards(uid1);
        List<Long> rightCards = deckService.getParticipantCards(uid2);
        return new SessionObject(sessionId, uid1, uid2, simpMessagingTemplate, leftCards, rightCards, SessionType.Practice, null, map);
    }

    private SessionObject createPveSessionObject(String sessionId, long uid1, Long scenarioId, GameMap map) {
        List<Long> leftCards = uid1 >= 0 ? deckService.getSelectedCards(uid1) : List.of();
        return new SessionObject(sessionId, uid1, -1, simpMessagingTemplate, leftCards, List.of(), SessionType.PVE, scenarioId, map);
    }
}
