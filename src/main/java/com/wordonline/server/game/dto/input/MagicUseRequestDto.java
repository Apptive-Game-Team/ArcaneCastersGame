package com.wordonline.server.game.dto.input;

import com.wordonline.server.game.domain.object.Vector3;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MagicUseRequestDto {
    private String type;
    // The magics.id of the single card the player cast.
    private long magicId;
    private int id;
    private Vector3 position;
}
