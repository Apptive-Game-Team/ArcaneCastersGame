package com.wordonline.server.game.dto.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BotThoughtInfoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesTheClientBotThoughtContract() {
        BotThoughtInfoDto dto = new BotThoughtInfoDto(
                Master.RightPlayer,
                "combo.mob-cluster",
                "Explosion targets three mobs.",
                List.of(34L),
                new Vector3(5, 0, 6));

        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(json.get("type").asText()).isEqualTo("botThought");
        assertThat(json.get("botSide").asText()).isEqualTo("RightPlayer");
        assertThat(json.get("ruleId").asText()).isEqualTo("combo.mob-cluster");
        assertThat(json.get("reason").asText()).contains("three mobs");
        assertThat(json.get("cards").get(0).asLong()).isEqualTo(34L);
        assertThat(json.get("target").get("z").asDouble()).isEqualTo(6.0);
    }
}
