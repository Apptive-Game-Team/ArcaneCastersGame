package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.dto.Master;

/**
 * Where a {@link BotAgent} gets its next cast from.
 *
 * <p>Called on the shared bot executor, whose rejection policy runs the tick on the game loop
 * thread, so an implementation must return quickly and never wait on the network. A null decision
 * means the bot does nothing this tick.
 */
public interface BotDecisionSource {

    BotBrain.InputDecision think(BotEye botEye, Parameters parameters, Master botSide, boolean overdue);
}
