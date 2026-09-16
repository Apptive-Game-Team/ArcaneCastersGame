package com.wordonline.server.game.dto.input;

import com.wordonline.server.game.domain.object.Vector3;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InputRequestDto {
    private String type;
    // magics.id of the one card this message is about.
    private long magicId;
    private int id;
    private Vector3 position;

    public MagicUseRequestDto toMagicUse() {
        if (!type.equals("useMagic")) {
            throw new IllegalArgumentException("InputRequestDto type is not 'useMagic'");
        }

        return new MagicUseRequestDto("useMagic", magicId, id, position);
    }

    public CardAimRequestDto toCardAim() {
        if (!type.equals("selectCard") && !type.equals("unselectCard")) {
            throw new IllegalArgumentException("InputRequestDto type is not 'selectCard' or 'unselectCard'");
        }

        return new CardAimRequestDto(type, magicId, id);
    }
}
