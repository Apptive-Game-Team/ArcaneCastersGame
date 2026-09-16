package com.wordonline.server.statistic.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.wordonline.server.deck.dto.CardDto;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.statistic.dto.GameResultDto;
import com.wordonline.server.statistic.dto.GameResultDto.StatisticDeckDto;
import com.wordonline.server.statistic.dto.GameResultDto.StatisticMagicDto;

import lombok.Setter;

@Setter
public class GameResultBuilder {

    private long leftUserId;
    private long rightUserId;
    private final List<StatisticDeckDto> deckDtos = new ArrayList<>();
    private final List<StatisticMagicDto> magicDtos = new ArrayList<>();
    private final Map<String, UpdateTimeStatistic> updateTimeStatisticMap = new HashMap<>();

    private final LocalDateTime startTime = LocalDateTime.now();

    // Statistic name for the interval between the start of two consecutive frames.
    // Unlike the per-GameSystem entries this measures scheduling, not CPU work.
    public static final String FRAME_STATISTIC_NAME = "Frame";

    // Statistic name for the one frame that never finished when the watchdog reaped a
    // stalled session. Frame intervals are recorded when the NEXT frame starts, so the
    // stall itself would otherwise be invisible; it is kept out of the Frame series so
    // the healthy-frame min/max/mean stay uncontaminated.
    public static final String STALLED_FRAME_STATISTIC_NAME = "StalledFrame";

    private Long lastFrameStartNs;

    public void addInterval(String name, long interval) {
        UpdateTimeStatistic statistic = updateTimeStatisticMap.computeIfAbsent(name,
                k -> new UpdateTimeStatistic());
        statistic.addInterval(interval);
    }

    // The first frame has no predecessor, so there is no interval to record yet.
    public void recordFrameStart(long nowNanos) {
        Long previous = lastFrameStartNs;
        lastFrameStartNs = nowNanos;
        if (previous == null) {
            return;
        }
        addInterval(FRAME_STATISTIC_NAME, nowNanos - previous);
    }

    public void recordDeck(long userId, List<CardDto> deckCards) {
        Map<Long, Long> counts = deckCards.stream()
                .collect(Collectors.groupingBy(CardDto::id, Collectors.counting()));

        for (Map.Entry<Long, Long> entry : counts.entrySet()) {
            long magicId = entry.getKey();
            int count = entry.getValue().intValue();

            Optional<StatisticDeckDto> existing = this.deckDtos.stream()
                    .filter(d -> d.magicId() == magicId && d.userId() == userId)
                    .findFirst();

            if (existing.isPresent()) {
                StatisticDeckDto old = existing.get();
                this.deckDtos.remove(old);
                this.deckDtos.add(new StatisticDeckDto(magicId, userId, old.count() + count));
            } else {
                this.deckDtos.add(new StatisticDeckDto(magicId, userId, count));
            }
        }
    }

    public void recordMagic(long userId, long magicId) {
        magicDtos.stream()
                .filter(magicDto -> magicDto.belongsTo(userId, magicId))
                .findAny()
                .ifPresentOrElse(
                        StatisticMagicDto::add,
                        () -> magicDtos.add(new StatisticMagicDto(userId, magicId))
                );
    }

    public GameResultDto build(Master loser, SessionType sessionType) {
        if (loser == Master.RightPlayer) {
            return build(sessionType, GameOutcome.WIN, leftUserId, rightUserId);
        }
        if (loser == Master.LeftPlayer) {
            return build(sessionType, GameOutcome.WIN, rightUserId, leftUserId);
        }
        return build(sessionType, GameOutcome.DRAW, null, null);
    }

    // For sessions the watchdog force-ends: flushes whatever accumulated up to the
    // stall, so the frame statistics survive as evidence of how the loop degraded.
    // nowNanos must come from System.nanoTime(), the clock recordFrameStart is fed with.
    public GameResultDto buildAbandoned(SessionType sessionType, long nowNanos) {
        if (lastFrameStartNs != null) {
            addInterval(STALLED_FRAME_STATISTIC_NAME, nowNanos - lastFrameStartNs);
        }
        return build(sessionType, GameOutcome.ABANDONED, null, null);
    }

    private GameResultDto build(SessionType sessionType, GameOutcome outcome, Long winId, Long lossId) {
        Duration duration = Duration.between(startTime, LocalDateTime.now());

        return new GameResultDto(
                sessionType,
                outcome,
                winId,
                lossId,
                duration,
                deckDtos,
                magicDtos,
                updateTimeStatisticMap
        );
    }
}
