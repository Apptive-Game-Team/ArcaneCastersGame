package com.wordonline.server.game.dto;

import com.wordonline.server.game.domain.magic.ElementType;

// One row of the magics table. A card is a magic now, so there is no card list to carry.
public record MagicInfoDto(
        Long id,
        String name,
        ElementType element
) {

}
