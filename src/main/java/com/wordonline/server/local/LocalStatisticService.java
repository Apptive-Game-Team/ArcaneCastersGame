package com.wordonline.server.local;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.wordonline.server.deck.service.DeckService;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.statistic.domain.GameResultBuilder;
import com.wordonline.server.statistic.dto.GameResultDto;
import com.wordonline.server.statistic.repository.StatisticRepository;
import com.wordonline.server.statistic.service.StatisticService;

import lombok.extern.slf4j.Slf4j;

/**
 * Statistics for a locally hosted server. The result is built exactly as on a shared server, from
 * the same {@link GameResultBuilder}, but instead of being written to {@code statistic_games} it is
 * printed as {@code matchStats} and {@code matchResult} lines; the client reports it to the lobby.
 * Nothing is written to the database.
 */
@Slf4j
@Service
@Profile("local")
public class LocalStatisticService extends StatisticService {

    private final LocalProtocol protocol;
    private final BuildProperties buildProperties;

    private final Map<GameContext, FrameRateRecorder> frameRecorders = new ConcurrentHashMap<>();
    // Sessions ended from outside the match (a newer local session or a shutdown request). Their
    // result is ABANDONED whatever the board looked like when the loop stopped.
    private final Set<GameContext> abandonedContexts = ConcurrentHashMap.newKeySet();
    // Matches whose result lines are not written yet; removed only after both lines are out.
    private final Set<GameContext> unreportedContexts = ConcurrentHashMap.newKeySet();

    public LocalStatisticService(StatisticRepository statisticRepository, DeckService deckService,
                                 LocalProtocol protocol, BuildProperties buildProperties) {
        super(statisticRepository, deckService);
        this.protocol = protocol;
        this.buildProperties = buildProperties;
    }

    @Override
    public void createBuilder(GameContext gameContext) {
        super.createBuilder(gameContext);
        frameRecorders.put(gameContext, new FrameRateRecorder());
        unreportedContexts.add(gameContext);
    }

    /** Whether this match still has to write its matchStats and matchResult lines. */
    public boolean hasUnreportedResult(GameContext gameContext) {
        return unreportedContexts.contains(gameContext);
    }

    @Override
    public void saveFrameStart(GameContext gameContext, long nowNanos) {
        super.saveFrameStart(gameContext, nowNanos);
        FrameRateRecorder recorder = frameRecorders.get(gameContext);
        if (recorder != null) {
            recorder.recordFrameStart(nowNanos);
        }
    }

    /** Makes the coming end of this match report ABANDONED; call before closing its loop. */
    public void markAbandoned(GameContext gameContext) {
        abandonedContexts.add(gameContext);
    }

    @Override
    public Optional<Long> saveGameResult(GameContext gameContext, Master loser, SessionType sessionType) {
        GameResultBuilder builder = gameResultBuilderMap.remove(gameContext);
        if (builder == null) {
            return Optional.empty();
        }
        GameResultDto result = abandonedContexts.remove(gameContext)
                ? builder.buildAbandoned(sessionType, System.nanoTime())
                : builder.build(loser, sessionType);
        report(gameContext, result);
        return Optional.empty();
    }

    @Override
    public Optional<Long> saveAbandonedGameResult(GameContext gameContext, SessionType sessionType) {
        GameResultBuilder builder = gameResultBuilderMap.remove(gameContext);
        if (builder == null) {
            return Optional.empty();
        }
        abandonedContexts.remove(gameContext);
        report(gameContext, builder.buildAbandoned(sessionType, System.nanoTime()));
        return Optional.empty();
    }

    private void report(GameContext gameContext, GameResultDto result) {
        String sessionId = gameContext.getSessionObject().getSessionId();
        int tickRate = gameContext.getTickRate();
        FrameRateRecorder recorder = frameRecorders.remove(gameContext);

        double meanFps = recorder == null ? 0 : recorder.meanFps();
        double p10Fps = recorder == null ? 0 : recorder.p10Fps();
        protocol.matchStats(new LocalMatchStats(sessionId, tickRate, round(meanFps), round(p10Fps)));

        protocol.matchResult(new LocalMatchResult(
                sessionId,
                result.outcome(),
                result.winUserId(),
                result.lossUserId(),
                result.duration().toMillis(),
                gameContext.getFrameNum(),
                tickRate,
                buildProperties.getVersion(),
                deckEntries(result),
                magicEntries(result)));
        unreportedContexts.remove(gameContext);
        log.info("[Local] Match reported; sessionId: {}, outcome: {}, meanFps: {}, p10Fps: {}",
                sessionId, result.outcome(), meanFps, p10Fps);
    }

    private static List<LocalMatchResult.Entry> deckEntries(GameResultDto result) {
        return result.decks().stream()
                .map(deck -> new LocalMatchResult.Entry(deck.userId(), deck.magicId(), deck.count()))
                .toList();
    }

    private static List<LocalMatchResult.Entry> magicEntries(GameResultDto result) {
        return result.magics().stream()
                .map(magic -> new LocalMatchResult.Entry(magic.getUserId(), magic.getMagicId(), magic.getCount()))
                .toList();
    }

    private static double round(double fps) {
        return Math.round(fps * 10) / 10.0;
    }
}
