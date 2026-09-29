package com.wordonline.server.game.dto.input;

public enum InputResultCode {
    SUCCESS,

    FAIL_INSUFFICIENT_MANA,
    FAIL_LACK_OF_CARD,
    FAIL_INVALID_PLACE,
    FAIL_INVALID_MAGIC,
    // The match result is decided and the loop is only playing out its last second.
    FAIL_GAME_ENDED,
}