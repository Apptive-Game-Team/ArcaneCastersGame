package com.wordonline.server.game.dto.bot;

import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

import java.util.List;

/** A bot decision explanation sent over the existing frame-info destination. */
public record BotThoughtInfoDto(
        String type,
        Master botSide,
        String ruleId,
        String reason,
        List<Long> cards,
        Vector3 target
) {
    public BotThoughtInfoDto(Master botSide,
                             String ruleId,
                             String reason,
                             List<Long> cards,
                             Vector3 target) {
        this("botThought", botSide, ruleId, reason, List.copyOf(cards), new Vector3(target));
    }
}
