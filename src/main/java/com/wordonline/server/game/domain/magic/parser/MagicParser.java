package com.wordonline.server.game.domain.magic.parser;

import com.wordonline.server.game.domain.magic.Magic;

public interface MagicParser {
    // Resolves one magic card the player is holding, and refuses it when the player does not own it.
    Magic parseMagic(long userId, long magicId);
}
