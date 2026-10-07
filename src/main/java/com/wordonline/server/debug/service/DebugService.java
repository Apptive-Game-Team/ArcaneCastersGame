package com.wordonline.server.debug.service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.wordonline.server.debug.dto.DebugActionResponseDto;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.debug.dto.DebugGameRequestDto;
import com.wordonline.server.debug.dto.DebugGameResponseDto;
import com.wordonline.server.debug.dto.DebugMagicInfoDto;
import com.wordonline.server.debug.dto.DebugPrefabInfoDto;
import com.wordonline.server.debug.dto.DebugSpawnPrefabRequestDto;
import com.wordonline.server.debug.dto.DebugSummonMagicRequestDto;
import com.wordonline.server.deck.service.DeckService;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.repository.MagicRepository;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.service.SessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebugService {

    private static final AtomicInteger sessionIdCounter = new AtomicInteger(1);
    private static final String DEBUG_SESSION_PREFIX = "debug-";
    private static final String DEBUG_PVE_SESSION_PREFIX = "debug-pve-";
    private static final String DEBUG_PRACTICE_SESSION_PREFIX = "debug-practice-";

    private final SessionService sessionService;
    private final DeckService deckService;
    private final DatabaseMagicParser magicParser;
    private final MagicRepository magicRepository;
    private final BotPersonaService botPersonaService;

    private SessionObject debugSession;

    public DebugGameResponseDto enterPracticeSession(DebugGameRequestDto debugGameRequestDto) {
        BotPersona opponent = botPersonaService.findRandomEnabled()
                .orElseThrow(() -> new IllegalStateException("No enabled bot persona is available."));
        DebugGameResponseDto dto = new DebugGameResponseDto(
                createPracticeDebugSession(debugGameRequestDto.userId(), opponent.userId())
        );
        log.info(
                "Entering practice session userId: {}, botUserId: {}, botName: {}",
                debugGameRequestDto.userId(),
                opponent.userId(),
                opponent.name()
        );
        return dto;
    }

    public DebugGameResponseDto enterPveSession(DebugGameRequestDto debugGameRequestDto) {
        DebugGameResponseDto dto = new DebugGameResponseDto(
                createPveDebugSession(debugGameRequestDto.userId(), -1, debugGameRequestDto.scenarioId())
        );
        log.info("Entering PVE debug session userId: {}, scenarioId: {}",
                debugGameRequestDto.userId(), debugGameRequestDto.scenarioId());
        return dto;
    }

    public DebugGameResponseDto enterTestSession(DebugGameRequestDto debugGameRequestDto) {
        DebugGameResponseDto dto = new DebugGameResponseDto(getSessionId());
        log.info("Entering test session userId: {}, side: {}", debugGameRequestDto.userId(), debugGameRequestDto.side());
        long userId = debugGameRequestDto.userId();
        if (debugGameRequestDto.side() == Master.LeftPlayer) {
            log.info("left");
            debugSession.setLeftUser(
                    userId,
                    deckService.getSelectedCards(userId)
            );
        } else if (debugGameRequestDto.side() == Master.RightPlayer) {
            log.info("right");
            debugSession.setRightUser(
                    userId,
                    deckService.getSelectedCards(userId)
            );
        }
        return dto;
    }

    private String getSessionId() {
        if (isActive(debugSession)) {
            return debugSession.getSessionId();
        }

        return createDebugSession();
    }

    private String createDebugSession() {
        return createDebugSession(0, 0);
    }

    private String createDebugSession(long uid1, long uid2) {
        return createDebugSession(DEBUG_SESSION_PREFIX, uid1, uid2, SessionType.PVP, null);
    }

    private String createPveDebugSession(long uid1, long uid2, Long scenarioId) {
        return createDebugSession(DEBUG_PVE_SESSION_PREFIX, uid1, uid2, SessionType.PVE, scenarioId);
    }

    private String createPracticeDebugSession(long uid1, long uid2) {
        return createDebugSession(DEBUG_PRACTICE_SESSION_PREFIX, uid1, uid2, SessionType.Practice, null);
    }

    private String createDebugSession(String sessionPrefix, long uid1, long uid2, SessionType sessionType, Long scenarioId) {
        SessionDto sessionDto = new SessionDto(
                sessionPrefix + sessionIdCounter.getAndIncrement(),
                uid1,
                uid2,
                sessionType,
                scenarioId
        );
        sessionService.createSession(sessionDto);
        debugSession = sessionService.getSessionObject(sessionDto.sessionId());
        return sessionDto.sessionId();
    }

    private boolean isActive(SessionObject debugSession) {
        if (debugSession == null) {
            return false;
        }
        return debugSession.getGameLoop().is_running();
    }

    public DebugActionResponseDto summonMagic(DebugSummonMagicRequestDto requestDto) {
        SessionObject session = sessionService.getSessionObject(requestDto.sessionId());
        if (session != null && session.getSessionType() == SessionType.Playground)
            return new DebugActionResponseDto(false, "Use the owner-only playground API.");
        if (session == null || !session.getGameLoop().is_running()) {
            log.warn("summonMagic: session not found or not running: {}", requestDto.sessionId());
            return new DebugActionResponseDto(false, "Session not found or not running.");
        }

        if (requestDto.magicId() == null) {
            log.warn("summonMagic: magicId missing");
            return new DebugActionResponseDto(false, "Magic id is required.");
        }
        if (requestDto.magicId() <= DatabaseMagicParser.INVALID_MAGIC_ID) {
            log.warn("summonMagic: magicId invalid {}", requestDto.magicId());
            return new DebugActionResponseDto(false, "Magic id must be positive.");
        }

        Magic magic = resolveMagic(requestDto);
        if (magic == null) {
            log.warn("summonMagic: magic not found. magicId: {}", requestDto.magicId());
            return new DebugActionResponseDto(false, String.format("Magic not found for id: %s", requestDto.magicId()));
        }

        GameContext gameContext = session.getGameContext();
        magic.run(gameContext, requestDto.master(), requestDto.position());
        log.info("summonMagic: magicId {} summoned for {} in session {}", requestDto.magicId(), requestDto.master(), requestDto.sessionId());
        return new DebugActionResponseDto(true, "Magic summoned successfully.");
    }

    public DebugActionResponseDto spawnPrefab(DebugSpawnPrefabRequestDto requestDto) {
        SessionObject session = sessionService.getSessionObject(requestDto.sessionId());
        if (session != null && session.getSessionType() == SessionType.Playground)
            return new DebugActionResponseDto(false, "Use the owner-only playground API.");
        if (session == null || !session.getGameLoop().is_running()) {
            log.warn("spawnPrefab: session not found or not running: {}", requestDto.sessionId());
            return new DebugActionResponseDto(false, "Session not found or not running.");
        }

        if (requestDto.prefabId() == null || requestDto.prefabId().isBlank()) {
            log.warn("spawnPrefab: prefabId missing");
            return new DebugActionResponseDto(false, "Prefab id is required.");
        }

        PrefabType prefabType = resolvePrefabType(requestDto);
        if (prefabType == null) {
            log.warn("spawnPrefab: prefab not found. prefabId: {}", requestDto.prefabId());
            return new DebugActionResponseDto(false, String.format("Prefab not found for id: %s", requestDto.prefabId()));
        }

        GameContext gameContext = session.getGameContext();
        new GameObject(requestDto.master(), prefabType, requestDto.position(), gameContext);
        log.info("spawnPrefab: prefabId {}, prefabType {} spawned for {} in session {}", requestDto.prefabId(), prefabType, requestDto.master(), requestDto.sessionId());
        return new DebugActionResponseDto(true, "Prefab spawned successfully.");
    }

    public List<DebugMagicInfoDto> getMagicList() {
        return magicRepository.getAllMagic().stream()
                .map(magicInfo -> new DebugMagicInfoDto(magicInfo.id(), magicInfo.name()))
                .toList();
    }

    public List<DebugPrefabInfoDto> getPrefabList() {
        return Arrays.stream(PrefabType.values())
                .map(prefabType -> new DebugPrefabInfoDto(prefabType.getBeanName(), prefabType.name()))
                .toList();
    }

    private Magic resolveMagic(DebugSummonMagicRequestDto requestDto) {
        return magicParser.parseMagicForBot(requestDto.magicId());
    }

    private PrefabType resolvePrefabType(DebugSpawnPrefabRequestDto requestDto) {
        String prefabId = requestDto.prefabId();
        return Arrays.stream(PrefabType.values())
                .filter(prefabType -> prefabType.getBeanName().equals(prefabId))
                .findFirst()
                .orElse(null);
    }
}
