package com.wordonline.server.statistic.repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.statistic.domain.UpdateTimeStatistic;
import com.wordonline.server.statistic.dto.GameResultDto;
import com.wordonline.server.statistic.dto.GameResultDto.StatisticDeckDto;
import com.wordonline.server.statistic.dto.GameResultDto.StatisticMagicDto;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StatisticRepository {

    private static final int EVENT_SCHEMA_VERSION = 1;

    private final JdbcClient jdbcClient;
    private final BuildProperties buildProperties;

    private final static String SAVE_GAME_RESULT = """
            INSERT INTO statistic_games(
                outcome,
                win_user_id,
                loss_user_id,
                duration,
                game_type,
                server_version,
                event_schema_version
            )
            VALUES(
                :outcome,
                :winUserId,
                :lossUserId,
                :duration,
                :gameType::game_type,
                :serverVersion,
                :eventSchemaVersion
            ) RETURNING id;
            """;
    private final static String SAVE_DECK = """
            INSERT INTO statistic_game_decks(user_id, statistic_game_id, magic_id, count)
            VALUES(:userId, :gameId, :magicId, :count);
            """;
    private final static String SAVE_MAGIC = """
            INSERT INTO statistic_game_magics(user_id, statistic_game_id, magic_id, count)
            VALUES(:userId, :gameId, :magicId, :count);
            """;

    private final static String SAVE_UPDATE_TIME = """
            INSERT INTO statistic_update_time(statistic_game_id, name, min_interval_ns, max_interval_ns, mean_interval_ns)
            VALUES(:gameId, :name, :minInterval, :maxInterval, :meanInterval);
            """;

    public long saveGameResultDto(GameResultDto gameResultDto) {
        long gameId = saveGame(gameResultDto);
        saveDeck(gameId, gameResultDto.decks());
        saveMagic(gameId, gameResultDto.magics());
        saveUpdateTime(gameId, gameResultDto.updateTimeStatisticMap());
        return gameId;
    }


    private void saveUpdateTime(long gameId, Map<String, UpdateTimeStatistic> updateTimeStatisticMap) {
        updateTimeStatisticMap.forEach((key, value) -> jdbcClient.sql(SAVE_UPDATE_TIME)
                .param("gameId", gameId)
                .param("minInterval", value.getMinInterval())
                .param("maxInterval", value.getMaxInterval())
                .param("meanInterval", value.getMeanInterval())
                .param("name", key)
                .update());
    }

    private long saveGame(GameResultDto gameResultDto) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql(SAVE_GAME_RESULT)
                .param("outcome", gameResultDto.outcome().name())
                .param("gameType", gameResultDto.sessionType().name())
                .param("winUserId", gameResultDto.winUserId())
                .param("lossUserId", gameResultDto.lossUserId())
                .param("duration", gameResultDto.duration().toSeconds())
                .param("serverVersion", buildProperties.getVersion())
                .param("eventSchemaVersion", EVENT_SCHEMA_VERSION)
                .update(keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void saveDeck(long gameId, List<StatisticDeckDto> deckDtos) {
        deckDtos.forEach(deckDto -> {
            jdbcClient.sql(SAVE_DECK)
                    .param("userId", deckDto.userId())
                    .param("gameId", gameId)
                    .param("magicId", deckDto.magicId())
                    .param("count", deckDto.count())
                    .update();
        });
    }

    private void saveMagic(long gameId, List<StatisticMagicDto> magicDtos) {
        magicDtos.forEach(magicDto -> {
            jdbcClient.sql(SAVE_MAGIC)
                    .param("userId", magicDto.getUserId())
                    .param("gameId", gameId)
                    .param("magicId", magicDto.getMagicId())
                    .param("count", magicDto.getCount())
                    .update();
        });
    }
}
