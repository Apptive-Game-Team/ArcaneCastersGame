package com.wordonline.server.game.domain.magic.parser;

import java.util.Set;

import com.wordonline.server.game.domain.magic.Magic;

public interface MagicParser {
    // Resolves one magic card the player is holding, and refuses it when the player does not own it.
    // ownedMagicIds is read when the session is created, so this never touches the database.
    Magic parseMagic(long userId, long magicId, Set<Long> ownedMagicIds);
}
