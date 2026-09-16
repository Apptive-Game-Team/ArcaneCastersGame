package com.wordonline.server.statistic.dto;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.statistic.domain.GameOutcome;
import com.wordonline.server.statistic.domain.UpdateTimeStatistic;

import lombok.AllArgsConstructor;
import lombok.Getter;

// winUserId/lossUserId are null unless outcome is WIN.
public record GameResultDto(
        SessionType sessionType,
        GameOutcome outcome,
        Long winUserId,
        Long lossUserId,
        Duration duration,
        List<StatisticDeckDto> decks,
        List<StatisticMagicDto> magics,
        Map<String, UpdateTimeStatistic> updateTimeStatisticMap
) {
    // One row of the deck the player brought into the match: a magic and how many
    // copies of it were in the deck. Distinct from StatisticMagicDto, which counts casts.
    public record StatisticDeckDto(
            long magicId,
            long userId,
            int count
    ) {

    }

    @Getter
    @AllArgsConstructor
    public static class StatisticMagicDto {
        long magicId;
        long userId;
        int count;

        public StatisticMagicDto(long userId, long magicId) {
            this(magicId, userId, 1);
        }

        public boolean belongsTo(long userId, long magicId) {
            return this.userId == userId && this.magicId == magicId;
        }

        public void add() {
            count++;
        }
    }
}
