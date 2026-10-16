package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.repository.TagRepository;

/**
 * Whether a summon has anything to do on the field right now.
 *
 * <p>The value scorer prices a summon the same whether or not its unit can act. Measured on the
 * Skies deck, it summoned {@code wind_spirit}, which only dives at flying units, eleven times a
 * game, and {@code wind_totem} seven times, 72 of 76 times with nothing coming. Each such cast is
 * mana that does nothing. By {@link CardRole}:
 * <ul>
 *   <li>{@link CardRole#ANTI_AIR}: only with an enemy flying unit on the field.</li>
 *   <li>{@link CardRole#DEFENSE} and {@link CardRole#PUSHER}: only with an enemy unit on our half
 *       of the field, since they never move and wait for enemies to come to them.</li>
 *   <li>{@link CardRole#SUPPORT}: only with at least {@value #SUPPORT_MIN_ALLIES} of our units on
 *       the field to support.</li>
 *   <li>Fighters, economy, and summons with no role always count.</li>
 * </ul>
 */
final class SummonRelevance {

    static final int SUPPORT_MIN_ALLIES = 2;

    private SummonRelevance() {
    }

    static boolean relevant(Magic magic, ThinkContext context, Parameters parameters) {
        CardRole role = CardRole.of(magic);
        if (role == null) {
            return true;
        }
        return switch (role) {
            case ANTI_AIR -> context.objects().stream()
                    .anyMatch(object -> isEnemyUnit(object, context) && flies(object.type(), parameters));
            case DEFENSE, PUSHER -> {
                double half = context.playerPos().distance(BotSideUtil.getPlayerPosition(context.enemySide())) / 2;
                yield context.objects().stream()
                        .anyMatch(object -> isEnemyUnit(object, context)
                                && object.position().distance(context.playerPos()) <= half);
            }
            case SUPPORT -> context.objects().stream()
                    .filter(object -> object.master() == context.botSide())
                    .filter(BotVisibleObject::targetable)
                    .filter(object -> object.type() != PrefabType.Player)
                    .count() >= SUPPORT_MIN_ALLIES;
            case FIGHTER, ECONOMY -> true;
        };
    }

    private static boolean isEnemyUnit(BotVisibleObject object, ThinkContext context) {
        return object.master() == context.enemySide()
                && object.mob()
                && object.targetable()
                && object.type() != PrefabType.Player;
    }

    private static boolean flies(PrefabType type, Parameters parameters) {
        return type != null && parameters.getValueOrDefault(
                TagRepository.toGameObjectName(type), ParameterKey.SPAWN_HEIGHT.dbName(), 0.0) > 0;
    }
}
