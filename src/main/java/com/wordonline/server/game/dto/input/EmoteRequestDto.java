package com.wordonline.server.game.dto.input;

/**
 * The emote name the player sent, still a raw string here.
 * {@link com.wordonline.server.game.controller.InputController} resolves it against
 * {@link com.wordonline.server.game.dto.Emote} so an unknown name can be logged and dropped
 * instead of failing {@link InputRequestDto#toEmote()} itself.
 */
public record EmoteRequestDto(
        String type,
        String emote
) {
}
