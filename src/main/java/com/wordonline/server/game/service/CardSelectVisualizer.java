package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.CardSelectedStatusEffect;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.IdleAuraStatusEffect;

import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Puts an idle aura of the aimed card's element on the caster, so the opponent can see a cast
 * coming. The element comes from {@code magics.element} now rather than from an element card.
 *
 * <p>A magic whose element is {@code None} gets no aura, so the aura alone cannot tell the client
 * that a card is selected. {@link CardSelectedStatusEffect} carries that on its own and goes on the
 * player for every selected card, element or not; the client raises the staff while it is there.
 */
@RequiredArgsConstructor
public class CardSelectVisualizer {

    private final DatabaseMagicParser magicParser;

    public void selectCard(GameContext gameContext, long userId, long magicId) {
        GameObject player = findPlayer(gameContext, userId);
        selectIdleAura(player, magicId);
        findCardSelected(player).ifPresentOrElse(
                CardSelectedStatusEffect::select,
                () -> player.addComponent(new CardSelectedStatusEffect(player)));
    }

    public void unselectCard(GameContext gameContext, long userId, long magicId) {
        GameObject player = findPlayer(gameContext, userId);
        unselectIdleAura(player, magicId);
        findCardSelected(player).ifPresent(CardSelectedStatusEffect::unselect);
    }

    private void selectIdleAura(GameObject player, long magicId) {
        toElement(magicId).ifPresent(element -> {
            if (hasIdleAura(player, element)) {
                return;
            }
            player.addComponent(new IdleAuraStatusEffect(player, element));
        });
    }

    private void unselectIdleAura(GameObject player, long magicId) {
        toElement(magicId).ifPresent(element -> findIdleAuras(player)
                .filter(effect -> effect.getElement() == element)
                .forEach(IdleAuraStatusEffect::cancel));
    }

    private GameObject findPlayer(GameContext gameContext, long userId) {
        return gameContext.findPlayerGameObject(userId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + userId));
    }

    private boolean hasIdleAura(GameObject player, ElementType element) {
        return findIdleAuras(player)
                .anyMatch(effect -> effect.getElement() == element);
    }

    private Stream<IdleAuraStatusEffect> findIdleAuras(GameObject player) {
        return Stream.concat(
                player.getComponents(IdleAuraStatusEffect.class).stream(),
                player.getComponentsToAdd().stream()
                        .filter(IdleAuraStatusEffect.class::isInstance)
                        .map(IdleAuraStatusEffect.class::cast)
        );
    }

    // An expired marker stays in the component list until the loop flushes removals at the end of
    // the frame, so skip it: selecting a card in the same frame as a cast must build a new one
    // rather than count up an effect the player object has already dropped.
    private Optional<CardSelectedStatusEffect> findCardSelected(GameObject player) {
        return Stream.concat(
                        player.getComponents(CardSelectedStatusEffect.class).stream(),
                        player.getComponentsToAdd().stream()
                                .filter(CardSelectedStatusEffect.class::isInstance)
                                .map(CardSelectedStatusEffect.class::cast))
                .filter(effect -> !effect.isExpired())
                .findFirst();
    }

    /** A magic with no element gets no aura, the way a cast type card used to get none. */
    private Optional<ElementType> toElement(long magicId) {
        Magic magic = magicParser.getMagic(magicId);
        if (magic == null || magic.element == null || magic.element == ElementType.NONE) {
            return Optional.empty();
        }
        return Optional.of(magic.element);
    }
}
