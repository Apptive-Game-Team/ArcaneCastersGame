package com.wordonline.server.statistic.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.server.deck.dto.CardDto;
import com.wordonline.server.deck.service.DeckService;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.system.GameSystem;
import com.wordonline.server.statistic.domain.GameResultBuilder;
import com.wordonline.server.statistic.dto.GameResultDto;
import com.wordonline.server.statistic.repository.StatisticRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatisticService {

    protected final Map<GameContext, GameResultBuilder> gameResultBuilderMap = new ConcurrentHashMap<>();
    private final StatisticRepository statisticRepository;
    private final DeckService deckService;

    public void createBuilder(GameContext gameContext) {
        GameResultBuilder builder = new GameResultBuilder();

        long leftUserId = gameContext.getSessionObject().getLeftUserId();
        long rightUserId = gameContext.getSessionObject().getRightUserId();

        builder.setLeftUserId(leftUserId);
        builder.setRightUserId(rightUserId);

        saveDeck(leftUserId, gameContext.getSessionObject().getLeftDeckCardIds(), builder);
        saveDeck(rightUserId, gameContext.getSessionObject().getRightDeckCardIds(), builder);

        gameResultBuilderMap.put(gameContext, builder);
    }

    public void saveMagic(GameContext gameContext, long userId, long magicId) {
        getGameResultBuilder(gameContext)
                .ifPresent(builder ->
                        builder.recordMagic(userId, magicId)
                );
    }

    // The deck the session actually dealt from, not the selected deck read back out of the
    // database: a session started from a matchmaking snapshot can differ from what the user
    // has selected by the time the match ends.
    private void saveDeck(long userId, List<Long> magicIds, GameResultBuilder builder) {
        List<CardDto> deckCards = deckService.getCardsByMagicIds(magicIds);
        builder.recordDeck(userId, deckCards);
    }

    // Returns the statistic_games id so the caller can link the session lifecycle row,
    // or empty when this session never had a builder (debug sessions).
    @Transactional
    public Optional<Long> saveGameResult(GameContext gameContext, Master loser, SessionType sessionType) {
        GameResultBuilder builder = gameResultBuilderMap.remove(gameContext);
        if (builder == null) {
            return Optional.empty();
        }
        GameResultDto gameResultDto = builder.build(loser, sessionType);
        return Optional.of(statisticRepository.saveGameResultDto(gameResultDto));
    }

    // Called by the loop watchdog for a session whose loop stalled. Flushes whatever the
    // builder accumulated up to the stall, including the age of the frame that never
    // finished, so the degradation is visible in the recorded statistics.
    @Transactional
    public Optional<Long> saveAbandonedGameResult(GameContext gameContext, SessionType sessionType) {
        GameResultBuilder builder = gameResultBuilderMap.remove(gameContext);
        if (builder == null) {
            return Optional.empty();
        }
        GameResultDto gameResultDto = builder.buildAbandoned(sessionType, System.nanoTime());
        return Optional.of(statisticRepository.saveGameResultDto(gameResultDto));
    }

    // The statistic name stays the simple class name so rows already recorded for
    // each GameSystem keep the exact same value in the name column.
    public void saveUpdateTime(GameContext gameContext, Class<? extends GameSystem> clazz, long intervalNs) {
        getGameResultBuilder(gameContext)
                .ifPresent(builder ->
                        builder.addInterval(clazz.getSimpleName(), intervalNs)
                );
    }

    public void saveFrameStart(GameContext gameContext, long nowNanos) {
        getGameResultBuilder(gameContext)
                .ifPresent(builder ->
                        builder.recordFrameStart(nowNanos)
                );
    }

    private Optional<GameResultBuilder> getGameResultBuilder(GameContext gameContext) {
        return Optional.ofNullable(gameResultBuilderMap.get(gameContext));
    }
}
