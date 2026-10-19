package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.dto.Master;

/** The rule-based {@link BotBrain}, unchanged, behind the {@link BotDecisionSource} contract. */
public final class RuleDecisionSource implements BotDecisionSource {

    private final BotBrain botBrain;

    public RuleDecisionSource(BotBrain botBrain) {
        this.botBrain = botBrain;
    }

    @Override
    public BotBrain.InputDecision think(BotEye botEye, Parameters parameters, Master botSide, boolean overdue) {
        return botBrain.think(botEye, parameters, botSide, overdue);
    }

    public BotBrain botBrain() {
        return botBrain;
    }
}
