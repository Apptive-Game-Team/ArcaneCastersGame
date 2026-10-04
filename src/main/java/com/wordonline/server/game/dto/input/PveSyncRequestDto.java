package com.wordonline.server.game.dto.input;

/** Asks the server to resend the PVE objective and the script events the client has not received. */
public record PveSyncRequestDto(String type, int lastEventSeq) {
}
