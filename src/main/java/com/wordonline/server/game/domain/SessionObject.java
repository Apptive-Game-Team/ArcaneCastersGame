package com.wordonline.server.game.domain;

import com.wordonline.server.game.channel.FrameChannel;
import com.wordonline.server.game.channel.StompFrameChannel;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

@Getter
@Slf4j
// this class is used to store the session information
// it sends the frame information to the client
public class SessionObject {
    private final String sessionId;
    private long leftUserId;
    private long rightUserId;
    private final FrameChannel frameChannel;
    private final String url;
    // The spectator registry is keyed by the STOMP broadcast destination, so it is kept here even
    // though sending goes through the transport-agnostic frameChannel.
    private final String broadcastDestination;
    private final CardDeck leftUserCardDeck;
    private final CardDeck rightUserCardDeck;
    private List<Long> leftDeckCardIds;
    private List<Long> rightDeckCardIds;
    private final PingChecker pingChecker;
    private final SessionType sessionType;
    private final Long scenarioId;
    private final long randomSeed;
    // Session ids are random UUIDs, so a room list ordered by id says nothing about age. The admin
    // page needs this to tell a session created seconds ago from one that has been running for a while.
    private final Instant createdAt = Instant.now();

    // Emote input runs on a STOMP inbound thread, not on the loop thread, so several requests for
    // the same side can race here. The deadline is read and swapped with compareAndSet instead of a
    // lock; a losing thread's emote is simply dropped, which is the cooldown's own contract.
    private static final long EMOTE_COOLDOWN_MILLIS = 3_000;
    private final AtomicLong leftEmoteCooldownUntilMillis = new AtomicLong(0);
    private final AtomicLong rightEmoteCooldownUntilMillis = new AtomicLong(0);

    public Master getUserSide(long userId) {
        if (userId == leftUserId) {
            return Master.LeftPlayer;
        } else if (userId == rightUserId) {
            return Master.RightPlayer;
        } else {
            return null;
        }
    }

    /**
     * The user id playing that side, or {@code null} when the side is unknown. The reverse of
     * {@link #getUserSide(long)}: the bot input path carries only the side, so anything that has
     * to name the caster - match statistics, for one - resolves the id back through here.
     */
    public Long getUserId(Master master) {
        if (master == Master.LeftPlayer) {
            return leftUserId;
        } else if (master == Master.RightPlayer) {
            return rightUserId;
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
        this(sessionId, leftUserId, rightUserId,
                new StompFrameChannel(template, StompFrameChannel.frameInfoUrl(sessionId)),
                leftUserCards, rightUserCards, sessionType, scenarioId);
    }

    public SessionObject(String sessionId,
                         long leftUserId,
                         long rightUserId,
                         FrameChannel frameChannel,
                         List<Long> leftUserCards,
                         List<Long> rightUserCards,
                         SessionType sessionType,
                         Long scenarioId) {
        this.sessionId = sessionId;
        this.leftUserId = leftUserId;
        this.rightUserId = rightUserId;
        this.frameChannel = frameChannel;
        this.url = StompFrameChannel.frameInfoUrl(sessionId);
        this.broadcastDestination = url + "/0";
        this.leftDeckCardIds = List.copyOf(leftUserCards);
        this.rightDeckCardIds = List.copyOf(rightUserCards);
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
        frameChannel.send(userId, data);
    }

    // this method is used to broadcast frame information to spectators (userId = 0)
    public void broadcastFrameInfo(Object data) {
        if (!hasSpectators()) {
            return;
        }
        frameChannel.broadcast(data);
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
        sendToBothPlayersAndSpectators(data);
    }

    /** Sends an emote through the same destinations used for frame information. */
    public void sendEmote(Object data) {
        sendToBothPlayersAndSpectators(data);
    }

    private void sendToBothPlayersAndSpectators(Object data) {
        sendFrameInfo(leftUserId, data);
        if (rightUserId != leftUserId) {
            sendFrameInfo(rightUserId, data);
        }
        broadcastFrameInfo(data);
    }

    /**
     * Consumes the emote cooldown for {@code side} if it is currently free.
     *
     * @return {@code true} when the caller may send the emote, {@code false} when it lands inside
     *         the previous emote's cooldown window and must be dropped silently.
     */
    public boolean tryConsumeEmoteCooldown(Master side) {
        AtomicLong cooldownUntilMillis = emoteCooldownFor(side);
        if (cooldownUntilMillis == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long previousDeadline = cooldownUntilMillis.get();
        if (now < previousDeadline) {
            return false;
        }

        return cooldownUntilMillis.compareAndSet(previousDeadline, now + EMOTE_COOLDOWN_MILLIS);
    }

    private AtomicLong emoteCooldownFor(Master side) {
        if (side == Master.LeftPlayer) {
            return leftEmoteCooldownUntilMillis;
        } else if (side == Master.RightPlayer) {
            return rightEmoteCooldownUntilMillis;
        } else {
            return null;
        }
    }

    // Called from the debug HTTP endpoint, off the loop thread. The hand and the deck are plain
    // loop-thread collections now, so resetting them is queued like any other input. The user id
    // assignment stays inline because callers read it back straight after the call.
    public void setLeftUser(long userId, List<Long> cards) {
        leftUserId = userId;
        leftDeckCardIds = List.copyOf(cards);
        GameContext gameContext = getGameContext();
        gameContext.submitAction("setLeftUserDeck", () -> {
            gameContext.getGameSessionData().leftPlayerData.cards.clear();
            gameContext.getGameSessionData().leftCardDeck.setCards(cards);
        });
    }

    public void setRightUser(long userId, List<Long> cards) {
        rightUserId = userId;
        rightDeckCardIds = List.copyOf(cards);
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
