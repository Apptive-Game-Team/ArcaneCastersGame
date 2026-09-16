package com.wordonline.server.game.dto.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.object.Vector3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cast protocol the client is written against. A card is one magic, so every input message
 * carries one {@code magicId} and no card list.
 */
class InputRequestDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsTheUseMagicPayload() throws Exception {
        InputRequestDto request = objectMapper.readValue("""
                { "type": "useMagic", "magicId": 34, "id": 7,
                  "position": { "x": 9.0, "y": 0.0, "z": 5.0 } }
                """, InputRequestDto.class);

        MagicUseRequestDto magicUse = request.toMagicUse();

        assertThat(magicUse.getMagicId()).isEqualTo(34L);
        assertThat(magicUse.getId()).isEqualTo(7);
        assertThat(magicUse.getPosition()).isEqualTo(new Vector3(9f, 0f, 5f));
    }

    @Test
    void readsTheSelectCardPayload() throws Exception {
        InputRequestDto request = objectMapper.readValue(
                """
                { "type": "selectCard", "magicId": 34, "id": 7 }
                """, InputRequestDto.class);

        CardAimRequestDto aim = request.toCardAim();

        assertThat(aim.type()).isEqualTo("selectCard");
        assertThat(aim.magicId()).isEqualTo(34L);
        assertThat(aim.id()).isEqualTo(7);
    }

    @Test
    void readsTheUnselectCardPayload() throws Exception {
        InputRequestDto request = objectMapper.readValue(
                """
                { "type": "unselectCard", "magicId": 34, "id": 7 }
                """, InputRequestDto.class);

        assertThat(request.toCardAim().type()).isEqualTo("unselectCard");
        assertThat(request.toCardAim().magicId()).isEqualTo(34L);
    }

    // toggleCard and cancelCard were combination assembly; there is no combination to assemble.
    @Test
    void refusesTheRetiredAimMessageTypes() throws Exception {
        for (String retired : new String[] {"toggleCard", "cancelCard"}) {
            InputRequestDto request = objectMapper.readValue(
                    "{ \"type\": \"" + retired + "\", \"magicId\": 34, \"id\": 7 }", InputRequestDto.class);

            assertThatThrownBy(request::toCardAim).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
