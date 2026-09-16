package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.input.*;
import com.wordonline.server.game.util.SynchronousFlowPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Flow;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Scope("prototype")
@Component
public class MagicInputHandler {

    private final DatabaseMagicParser magicParser;

    private final SynchronousFlowPublisher<InputHandleEvent> inputEventPublisher = new SynchronousFlowPublisher<>();

    public void subscribe(Flow.Subscriber<InputHandleEvent> subscriber) {
        inputEventPublisher.subscribe(subscriber);
    }

    // Runs on the loop thread. The STOMP inbound thread queues the cast on the game action queue
    // instead of calling this directly, so card/mana deduction and the whole magic execution can
    // never interleave with a frame.
    public InputResponseDto handleInput(GameContext gameContext, long userId, MagicUseRequestDto inputRequestDto) {
        Master master = gameContext.getSessionObject().getUserSide(userId);
        PlayerData playerData = gameContext.getGameSessionData().getPlayerData(master);

        long magicId = inputRequestDto.getMagicId();
        if (magicId <= DatabaseMagicParser.INVALID_MAGIC_ID || inputRequestDto.getPosition() == null) {
            log.trace("{}: magic use request is missing magicId or position", master);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INVALID_MAGIC));
            return new InputResponseDto("Invalid magic.", false, InputResultCode.FAIL_INVALID_MAGIC, playerData.mana, inputRequestDto.getId(), -1);
        }

        if (!playerData.cards.contains(magicId)) {
            log.trace("{}: magic {} is not in hand", master, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_LACK_OF_CARD));
            return new InputResponseDto("Cannot use cards.", false, InputResultCode.FAIL_LACK_OF_CARD, playerData.mana, inputRequestDto.getId(), -1);
        }

        Magic magic = magicParser.parseMagic(userId, magicId);

        if (magic == null) {
            // The card names a magic the server does not know or the player does not own. Nothing
            // can be priced, so the card is discarded to the bottom of the deck free of charge.
            log.trace("{}: magic {} is not valid : could not resolve", master, magicId);
            discardCard(gameContext, master, playerData, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INVALID_MAGIC));
            return new InputResponseDto("Invalid magic.", true, InputResultCode.FAIL_INVALID_MAGIC, playerData.mana, inputRequestDto.getId(), -1);
        }

        Vector3 castOrigin = gameContext.findPlayerGameObject(master)
                .map(gameObject -> new Vector3(gameObject.getPosition()))
                .orElse(null);
        if (castOrigin == null) {
            log.trace("{}: magic {} is not valid : caster not found", master, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INVALID_PLACE));
            return new InputResponseDto("Caster is not found.", false, InputResultCode.FAIL_INVALID_PLACE, playerData.mana, inputRequestDto.getId(), -1);
        }
        Vector3 castPosition = clampToRange(
                castOrigin,
                clampToMapBounds(inputRequestDto.getPosition()),
                castRange(gameContext, magic)
        );

        boolean valid = playerData.useCard(magicId, manaCost(gameContext, magic));

        if (!valid) {
            log.trace("{}: magic {} is not valid : cannot use", master, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INSUFFICIENT_MANA));
            return new InputResponseDto("Insufficient mana.", false, InputResultCode.FAIL_INSUFFICIENT_MANA, playerData.mana, inputRequestDto.getId(), -1);
        }

        magic.run(gameContext, master, castOrigin, castPosition);
        gameContext.getGameSessionData().getCardDeck(master).returnCard(magicId);

        inputEventPublisher.publish(new InputHandleEvent(master, InputResultCode.SUCCESS, magic.id));
        return new InputResponseDto(true, InputResultCode.SUCCESS, playerData.mana, inputRequestDto.getId(), magic.id);
    }

    // Runs on the loop thread. BotAgent decides on the bot executor thread but dispatches through
    // the game action queue, so the cast itself lands here between frames.
    public InputResponseDto handleBotPlayerInput(GameContext gameContext, Master master, InputRequestDto inputRequestDto) {
        PlayerData playerData = gameContext.getGameSessionData().getPlayerData(master);

        long magicId = inputRequestDto.getMagicId();
        if (!playerData.cards.contains(magicId)) {
            log.trace("{}: magic {} is not in hand", master, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_LACK_OF_CARD));
            return new InputResponseDto("Cannot use cards.", false, InputResultCode.FAIL_LACK_OF_CARD,  playerData.mana, inputRequestDto.getId(), -1);
        }

        Magic magic = magicParser.getMagic(magicId);

        if (magic == null) {
            log.trace("{}: magic {} is not valid : could not resolve", master, magicId);
            discardCard(gameContext, master, playerData, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INVALID_MAGIC));
            return new InputResponseDto("Invalid magic.", true, InputResultCode.FAIL_INVALID_MAGIC, playerData.mana, inputRequestDto.getId(), -1);
        }

        Vector3 castOrigin = gameContext.findPlayerGameObject(master)
                .map(gameObject -> new Vector3(gameObject.getPosition()))
                .orElse(null);
        if (castOrigin == null) {
            log.trace("{}: magic {} is not valid : caster not found", master, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INVALID_PLACE));
            return new InputResponseDto("Caster is not found.", false, InputResultCode.FAIL_INVALID_PLACE, playerData.mana, inputRequestDto.getId(), -1);
        }
        Vector3 castPosition = clampToRange(
                castOrigin,
                clampToMapBounds(inputRequestDto.getPosition()),
                castRange(gameContext, magic)
        );

        boolean valid = playerData.useCard(magicId, manaCost(gameContext, magic));

        if (!valid) {
            log.trace("{}: magic {} is not valid : cannot use", master, magicId);
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INSUFFICIENT_MANA));
            return new InputResponseDto("Insufficient mana.", false, InputResultCode.FAIL_INSUFFICIENT_MANA, playerData.mana, inputRequestDto.getId(), -1);
        }

        magic.run(gameContext, master, castOrigin, castPosition);
        gameContext.getGameSessionData().getCardDeck(master).returnCard(magicId);

        inputEventPublisher.publish(new InputHandleEvent(master, InputResultCode.SUCCESS, magic.id));
        return new InputResponseDto(true, InputResultCode.SUCCESS, playerData.mana, inputRequestDto.getId(), magic.id);
    }

    public InputResponseDto handleBotMagicInput(GameContext gameContext,
                                                Master master,
                                                Magic magic,
                                                Vector3 position) {
        return handleBotMagicInput(gameContext, master, magic, position, null);
    }

    public InputResponseDto handleBotMagicInput(GameContext gameContext,
                                                Master master,
                                                Magic magic,
                                                Vector3 position,
                                                Vector3 castOrigin) {
        PlayerData playerData = gameContext.getGameSessionData().getPlayerData(master);

        if (magic == null) {
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INVALID_MAGIC));
            return new InputResponseDto("invalid bot magic", false, InputResultCode.FAIL_INVALID_MAGIC, playerData.mana, -1, -1);
        }

        Vector3 rangeOrigin = castOrigin == null
                ? gameContext.findPlayerGameObject(master)
                        .map(gameObject -> new Vector3(gameObject.getPosition()))
                        .orElse(null)
                : castOrigin;
        if (rangeOrigin == null) {
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INVALID_PLACE));
            return new InputResponseDto("invalid place", false, InputResultCode.FAIL_INVALID_PLACE, playerData.mana, -1, -1);
        }
        Vector3 castPosition = clampToRange(
                rangeOrigin,
                clampToMapBounds(position),
                castRange(gameContext, magic)
        );

        if (!playerData.spendMana(manaCost(gameContext, magic))) {
            inputEventPublisher.publish(InputHandleEvent.fail(master, InputResultCode.FAIL_INSUFFICIENT_MANA));
            return new InputResponseDto("insufficient mana", false, InputResultCode.FAIL_INSUFFICIENT_MANA, playerData.mana, -1, -1);
        }

        magic.run(gameContext, master, rangeOrigin, castPosition);
        inputEventPublisher.publish(new InputHandleEvent(master, InputResultCode.SUCCESS, magic.id));
        return new InputResponseDto(true, InputResultCode.SUCCESS, playerData.mana, -1, magic.id);
    }

    private void discardCard(GameContext gameContext, Master master, PlayerData playerData, long magicId) {
        playerData.cards.remove(Long.valueOf(magicId));
        gameContext.getGameSessionData().getCardDeck(master).returnCard(magicId);
    }

    private int manaCost(GameContext gameContext, Magic magic) {
        return (int) gameContext.getParameters().getValue(parameterKey(magic), "mana_cost");
    }

    private double castRange(GameContext gameContext, Magic magic) {
        return gameContext.getParameters().getValue(parameterKey(magic), "range");
    }

    // game_objects.name equals magics.name, so mana_cost and range are read under the magic's own
    // name. There is no cast type left to key them by.
    private static String parameterKey(Magic magic) {
        return magic.name;
    }

    private Vector3 clampToRange(Vector3 origin, Vector3 position, double range) {
        double distance = origin.distance(position);
        if (distance <= range || distance == 0) {
            return position;
        }

        return origin.plus(position.subtract(origin).normalize().multiply((float) range));
    }

    private static final float MAP_MIN_X = 0f;
    private static final float MAP_MAX_X = 18f;
    private static final float MAP_MIN_Z = 0f;
    private static final float MAP_MAX_Z = 10f;

    private Vector3 clampToMapBounds(Vector3 position) {
        return new Vector3(
                Math.clamp(position.getX(), MAP_MIN_X, MAP_MAX_X),
                position.getY(),
                Math.clamp(position.getZ(), MAP_MIN_Z, MAP_MAX_Z)
        );
    }
}
