package com.wordonline.server.game.dto.input;

/**
 * The card the player is currently aiming with, or has stopped aiming with.
 *
 * <p>One message carries both, told apart by {@code type}: {@code selectCard} and
 * {@code unselectCard}. There is no combination to assemble any more, so a player aims with exactly
 * one card at a time and {@code toggleCard} and {@code cancelCard} have nothing left to do.
 */
public record CardAimRequestDto(
        String type,
        long magicId,
        int id
) {
}
