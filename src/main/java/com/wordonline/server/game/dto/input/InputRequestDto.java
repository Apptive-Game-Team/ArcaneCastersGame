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
    // The raw emote name; com.wordonline.server.game.dto.Emote validates it.
    private String emote;
    // pveSync: the highest script event seq the client has received, 0 when none.
    private int lastEventSeq;

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

    public EmoteRequestDto toEmote() {
        if (!type.equals("emote")) {
            throw new IllegalArgumentException("InputRequestDto type is not 'emote'");
        }

        return new EmoteRequestDto(type, emote);
    }

    public PveSyncRequestDto toPveSync() {
        if (!type.equals("pveSync")) {
            throw new IllegalArgumentException("InputRequestDto type is not 'pveSync'");
        }

        return new PveSyncRequestDto(type, lastEventSeq);
    }
}
