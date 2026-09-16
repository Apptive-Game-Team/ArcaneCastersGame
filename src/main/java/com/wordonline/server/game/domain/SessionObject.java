package com.wordonline.server.game.domain;

import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.PingChecker;
import com.wordonline.server.game.service.CardDeck;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.WordOnlineLoop;
import com.wordonline.server.game.util.DeckSeedDeriver;
import com.wordonline.server.websocket.SpectatorSubscriptionRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

@Getter
@Slf4j
// this class is used to store the session information
// it sends the frame information to the client
public class SessionObject {
    private final String sessionId;
    private long leftUserId;
    private long rightUserId;
    private final SimpMessagingTemplate template;
    private final String url;
    // STOMP destinations are fixed strings, so they are built once instead of formatted on every
    // send. The broadcast one never changes; the per-user ones are rebuilt when a user id changes.
    private final String broadcastDestination;
    private String leftUserDestination;
    private String rightUserDestination;
    private final CardDeck leftUserCardDeck;
    private final CardDeck rightUserCardDeck;
    private final PingChecker pingChecker;
    private final SessionType sessionType;
    private final Long scenarioId;
    private final long randomSeed;
    // Session ids are random UUIDs, so a room list ordered by id says nothing about age. The admin
    // page needs this to tell a session created seconds ago from one that has been running for a while.
    private final Instant createdAt = Instant.now();

    public Master getUserSide(long userId) {
        if (userId == leftUserId) {
            return Master.LeftPlayer;
        } else if (userId == rightUserId) {
            return Master.RightPlayer;
        } else {
            return null;
        }
    }

    public boolean isLeftBot() {
        return leftUserId < 0;
    }

    public boolean isRightBot() {
        return rightUserId < 0;
    }

    @Setter
    private GameLoop gameLoop;

    // Wired by GameLoop.initializeLoop. Written on the session creation thread and read by the
    // loop thread every frame, so it is volatile; a session with no registry has no spectators.
    @Setter
    private volatile SpectatorSubscriptionRegistry spectatorSubscriptionRegistry;

    public GameContext getGameContext() {
        return gameLoop.getGameContext();
    }

    public SessionObject(String sessionId,
                         long leftUserId,
                         long rightUserId,
                         SimpMessagingTemplate template,
                         List<Long> leftUserCards,
                         List<Long> rightUserCards,
                         SessionType sessionType,
                         Long scenarioId) {
        this.sessionId = sessionId;
        this.leftUserId = leftUserId;
        this.rightUserId = rightUserId;
        this.template = template;
        this.url = String.format("/game/%s/frameInfos", sessionId);
        this.broadcastDestination = url + "/0";
        this.leftUserDestination = userDestination(leftUserId);
        this.rightUserDestination = userDestination(rightUserId);
        this.randomSeed = ThreadLocalRandom.current().nextLong();
        long leftDeckSeed = DeckSeedDeriver.forLeftDeck(randomSeed);
        long rightDeckSeed = DeckSeedDeriver.forRightDeck(randomSeed);
        this.leftUserCardDeck = new CardDeck(leftUserCards, leftDeckSeed);
        this.rightUserCardDeck = new CardDeck(rightUserCards, rightDeckSeed);
        log.trace("[Session] randomSeed={}, leftDeckSeed={}, rightDeckSeed={}, sessionId={}",
                randomSeed, leftDeckSeed, rightDeckSeed, sessionId);
        // Both callbacks fire on the PingChecker scheduler thread. Swapping a bot in or out changes
        // what the next frame ticks, so the work is queued for the loop thread instead of applied here.
        this.pingChecker = new PingChecker(leftUserId, rightUserId,
                userId -> submitBotToggle(userId, "activateBot", WordOnlineLoop::activateBotForUser),
                userId -> submitBotToggle(userId, "deactivateBot", WordOnlineLoop::deactivateBotForUser)
        );
        this.sessionType = sessionType;
        this.scenarioId = scenarioId;
    }

    public SessionObject(String sessionId,
                         long leftUserId,
                         long rightUserId,
                         SimpMessagingTemplate template,
                         List<Long> leftUserCards,
                         List<Long> rightUserCards,
                         SessionType sessionType) {
        this(sessionId, leftUserId, rightUserId, template, leftUserCards, rightUserCards, sessionType, null);
    }

    public SessionObject(String sessionId,
                         long leftUserId,
                         long rightUserId,
                         SimpMessagingTemplate template,
                         List<Long> leftUserCards,
                         List<Long> rightUserCards) {
        this(sessionId, leftUserId, rightUserId, template, leftUserCards, rightUserCards, SessionType.PVP, null);
    }

    private void submitBotToggle(long userId, String actionName, BiConsumer<WordOnlineLoop, Long> toggle) {
        if (getUserSide(userId) == null) {
            return;
        }
        if (gameLoop instanceof WordOnlineLoop wordOnlineLoop) {
            wordOnlineLoop.getGameContext()
                    .submitAction(actionName, () -> toggle.accept(wordOnlineLoop, userId));
        }
    }

    // this method is used to send the frame information to the client
    public void sendFrameInfo(long userId, Object data) {
        // Skip sending frame info to bots (negative user IDs)
        if (userId < 0) {
            return;
        }
        template.convertAndSend(destinationFor(userId), data);
    }

    // this method is used to broadcast frame information to spectators (userId = 0)
    public void broadcastFrameInfo(Object data) {
        if (!hasSpectators()) {
            return;
        }
        template.convertAndSend(broadcastDestination, data);
    }

    // convertAndSend serializes the payload before it reaches the broker, and the broker channel
    // dispatches inline on the game loop thread, so a broadcast with no subscriber costs a full
    // JSON encode per frame and is then dropped. Callers check this before building the payload.
    public boolean hasSpectators() {
        SpectatorSubscriptionRegistry registry = spectatorSubscriptionRegistry;
        return registry != null && registry.hasSubscribers(broadcastDestination);
    }

    /** Sends bot telemetry through the same destinations used for frame information. */
    public void sendBotThought(Object data) {
        sendFrameInfo(leftUserId, data);
        if (rightUserId != leftUserId) {
            sendFrameInfo(rightUserId, data);
        }
        broadcastFrameInfo(data);
    }

    private String destinationFor(long userId) {
        if (userId == leftUserId) {
            return leftUserDestination;
        }
        if (userId == rightUserId) {
            return rightUserDestination;
        }
        return userDestination(userId);
    }

    private String userDestination(long userId) {
        return String.format("%s/%d", url, userId);
    }

    // Called from the debug HTTP endpoint, off the loop thread. The hand and the deck are plain
    // loop-thread collections now, so resetting them is queued like any other input. The user id
    // assignment stays inline because callers read it back straight after the call.
    public void setLeftUser(long userId, List<Long> cards) {
        leftUserId = userId;
        leftUserDestination = userDestination(userId);
        GameContext gameContext = getGameContext();
        gameContext.submitAction("setLeftUserDeck", () -> {
            gameContext.getGameSessionData().leftPlayerData.cards.clear();
            gameContext.getGameSessionData().leftCardDeck.setCards(cards);
        });
    }

    public void setRightUser(long userId, List<Long> cards) {
        rightUserId = userId;
        rightUserDestination = userDestination(userId);
        GameContext gameContext = getGameContext();
        gameContext.submitAction("setRightUserDeck", () -> {
            gameContext.getGameSessionData().rightPlayerData.cards.clear();
            gameContext.getGameSessionData().rightCardDeck.setCards(cards);
        });
    }

    @Override
    public String toString() {
        float deltaTime = gameLoop.getGameContext().getDeltaTime();
        double fps = deltaTime > 0 ? 1 / deltaTime : 0.0;
        return String.format("Session(users: [%d, %d], isRunning: %s, currentFps: %.2f)",
                leftUserId,
                rightUserId,
                gameLoop.is_running(),
                fps);
    }
}
