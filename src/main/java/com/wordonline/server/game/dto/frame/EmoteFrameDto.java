package com.wordonline.server.game.dto.frame;

import com.wordonline.server.game.dto.Emote;
import com.wordonline.server.game.dto.Master;

/** Sent to both players and spectators over the existing frame-info destinations. */
public record EmoteFrameDto(String type, Master side, Emote emote) {
    public EmoteFrameDto(Master side, Emote emote) {
        this("emote", side, emote);
    }
}
