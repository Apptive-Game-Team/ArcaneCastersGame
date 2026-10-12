package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.dto.Master;

import java.util.List;

// What the bot saw at one frame, frozen. Built on the loop thread and handed to the bot executor,
// so the brain never touches a live GameObject, card list or mana counter while a frame is running.
// cardList holds the magics.id of every card in hand. observedAtSeconds is the game time of the
// frame, or NaN when the caller did not say; it is what lets the bot tell how fast things move.
public record BotEye(
        List<BotVisibleObject> gameObjectList,
        List<Long> cardList,
        int mana,
        int enemyPlayerHp,
        double observedAtSeconds
) {

    public BotEye(List<BotVisibleObject> gameObjectList, List<Long> cardList, int mana, int enemyPlayerHp) {
        this(gameObjectList, cardList, mana, enemyPlayerHp, Double.NaN);
    }

    // Must be called on the loop thread.
    public static BotEye observe(GameSessionData data, Master botSide) {
        return observe(data, botSide, Double.NaN);
    }

    // Must be called on the loop thread.
    public static BotEye observe(GameSessionData data, Master botSide, double gameTimeSeconds) {
        var playerData = BotSideUtil.getPlayerData(data, botSide);
        return new BotEye(
                data.gameObjects.stream().map(BotVisibleObject::of).toList(),
                List.copyOf(playerData.cards),
                playerData.mana,
                BotSideUtil.getPlayerData(data, BotSideUtil.getEnemySide(botSide)).hp,
                gameTimeSeconds
        );
    }
}
