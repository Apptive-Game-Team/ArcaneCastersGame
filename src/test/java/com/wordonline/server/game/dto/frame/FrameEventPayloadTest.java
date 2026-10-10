package com.wordonline.server.game.dto.frame;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.dto.CardInfoDto;
import com.wordonline.server.game.dto.sync.SyncInfoDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The client dispatches on the event type, so the wire shape is a contract.
 */
class FrameEventPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void hitEventCarriesTypeActorAndTarget() {
        JsonNode json = objectMapper.valueToTree(GameEventDto.hit(7, 12));

        assertThat(json.get("type").asText()).isEqualTo("hit");
        assertThat(json.get("actorId").asInt()).isEqualTo(7);
        assertThat(json.get("targetId").asInt()).isEqualTo(12);
    }

    @Test
    void frameCarriesItsEvents() {
        JsonNode json = objectMapper.valueToTree(frameWith(List.of(GameEventDto.hit(7, 12))));

        assertThat(json.get("events")).hasSize(1);
        assertThat(json.get("events").get(0).get("type").asText()).isEqualTo("hit");
    }

    @Test
    void frameWithoutEventsSerializesAnEmptyList() {
        JsonNode json = objectMapper.valueToTree(frameWith(List.of()));

        assertThat(json.get("events")).isEmpty();
    }

    @Test
    void syncFrameKeepsTheEventsOfTheFrameItReplaces() {
        FrameInfoDto frame = frameWith(List.of(GameEventDto.hit(7, 12)));

        SyncInfoDto sync = frame.toSyncDto(new SnapshotResponseDto(10, List.of(), List.of()), 20);

        assertThat(sync.getEvents()).isEqualTo(frame.getEvents());
        assertThat(objectMapper.valueToTree(sync).get("events")).hasSize(1);
    }

    @Test
    void syncFrameCarriesTheTickRateAsATopLevelField() {
        SyncInfoDto sync = frameWith(List.of()).toSyncDto(new SnapshotResponseDto(1, List.of(), List.of()), 60);

        JsonNode json = objectMapper.valueToTree(sync);

        assertThat(json.get("type").asText()).isEqualTo("sync");
        assertThat(json.get("tickRate").asInt()).isEqualTo(60);
    }

    private FrameInfoDto frameWith(List<GameEventDto> events) {
        return new FrameInfoDto(30, new CardInfoDto(), new ObjectsInfoDto(), gameSessionData(), events);
    }

    private GameSessionData gameSessionData() {
        return new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
    }
}
