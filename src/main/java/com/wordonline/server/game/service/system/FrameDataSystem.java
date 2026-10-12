package com.wordonline.server.game.service.system;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import com.wordonline.server.game.dto.CardInfoDto;
import com.wordonline.server.game.dto.frame.FrameInfoDto;
import com.wordonline.server.game.dto.frame.GameEventDto;
import com.wordonline.server.game.dto.frame.ObjectsInfoDto;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.service.GameContext;

import lombok.Getter;

@Getter
@Component
@Scope("prototype")
public class FrameDataSystem implements EarlyUpdateSystem, LateUpdateSystem {

    private FrameInfoDto leftFrameInfoDto;
    private FrameInfoDto rightFrameInfoDto;
    // Null whenever the session has no spectator subscribed: the broadcast payload is not worth
    // assembling for a message the broker would drop. Checked again every frame, so a spectator
    // that subscribes mid match is served from the next frame on.
    private FrameInfoDto broadcastFrameInfoDto;

    @Override
    public void earlyUpdate(GameContext gameContext) {
        CardInfoDto leftCardInfo = new CardInfoDto();
        CardInfoDto rightCardInfo = new CardInfoDto();

        ObjectsInfoDto objectsInfoDto = gameContext.getObjectsInfoDto();

        long remainingTime = gameContext.getGameTimer().getRemainingTimeSeconds();

        List<GameEventDto> events = gameContext.drainEvents();

        leftFrameInfoDto = new FrameInfoDto(remainingTime, leftCardInfo, objectsInfoDto, gameContext.getGameSessionData(), events);
        rightFrameInfoDto = new FrameInfoDto(remainingTime, rightCardInfo, objectsInfoDto, gameContext.getGameSessionData(), events);
        broadcastFrameInfoDto = hasSpectators(gameContext)
                ? FrameInfoDto.createBroadcastDto(
                        remainingTime,
                        objectsInfoDto,
                        gameContext.getGameSessionData(),
                        events
                )
                : null;

        // Charge Mana
        gameContext.getGameSessionData().leftPlayerData.manaCharger.chargeMana(gameContext.getGameSessionData().leftPlayerData, leftFrameInfoDto, gameContext.getDeltaTime());
        gameContext.getGameSessionData().rightPlayerData.manaCharger.chargeMana(gameContext.getGameSessionData().rightPlayerData, rightFrameInfoDto, gameContext.getDeltaTime());
        // Mana
        leftFrameInfoDto.setUpdatedMana(gameContext.getGameSessionData().leftPlayerData.mana);
        rightFrameInfoDto.setUpdatedMana(gameContext.getGameSessionData().rightPlayerData.mana);

        // Draw Cards
        gameContext.getGameSessionData().leftCardDeck.drawCard(gameContext.getGameSessionData().leftPlayerData, leftCardInfo, gameContext.getDeltaTime());
        gameContext.getGameSessionData().rightCardDeck.drawCard(gameContext.getGameSessionData().rightPlayerData, rightCardInfo, gameContext.getDeltaTime());
    }

    @Override
    public void lateUpdate(GameContext gameContext) {

        // Send Frame Info To Client
        gameContext.getSessionObject().sendFrameInfo(
                gameContext.getSessionObject().getLeftUserId(),
                leftFrameInfoDto
        );
        gameContext.getSessionObject().sendFrameInfo(
                gameContext.getSessionObject().getRightUserId(),
                rightFrameInfoDto
        );

        if (broadcastFrameInfoDto != null) {
            gameContext.getSessionObject().broadcastFrameInfo(broadcastFrameInfoDto);
        }
    }

    protected static boolean hasSpectators(GameContext gameContext) {
        SessionObject sessionObject = gameContext.getSessionObject();
        return sessionObject != null && sessionObject.hasSpectators();
    }
}
