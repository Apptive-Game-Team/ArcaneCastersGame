package com.wordonline.server.bot.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongFunction;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.bot.BotSideUtil;
import com.wordonline.server.game.domain.bot.BotSpellStats;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

/**
 * Turns one bot snapshot into the option lists and state an LLM request carries.
 *
 * <p>Magic options are the affordable magics in hand, priced by {@link BotSpellStats} so the bot
 * prices a cast exactly as the input handler charges it. Target options are the enemy units
 * nearest the bot's own base first, then the enemy base, then two defensive spots in front of the
 * bot's base. Runs on the bot executor and reads only the snapshot and the cached parameters.
 */
public final class LlmCandidateBuilder {

    /** Upper bound on target options, so the prompt stays small and the model's choice stays easy. */
    public static final int MAX_TARGETS = 12;
    /** Enemy units listed in the state. */
    static final int MAX_STATE_ENEMIES = 12;
    /** Own units listed in the state. */
    static final int MAX_STATE_ALLIES = 8;
    /** Distances, along the lane, of the defensive spots in front of the bot's own base. */
    private static final float[] DEFENSIVE_OFFSETS = {3f, 6f};

    private final LongFunction<Magic> magicLookup;

    public LlmCandidateBuilder(LongFunction<Magic> magicLookup) {
        this.magicLookup = magicLookup;
    }

    public LlmCandidates build(BotEye botEye, Parameters parameters, Master botSide) {
        Master enemySide = BotSideUtil.getEnemySide(botSide);
        Vector3 ownBase = BotSideUtil.getPlayerPosition(botSide);
        Vector3 enemyBase = BotSideUtil.getPlayerPosition(enemySide);
        BotSpellStats spellStats = new BotSpellStats(parameters);

        Map<String, LlmCandidates.MagicOption> magics = new LinkedHashMap<>();
        List<Map<String, Object>> hand = new ArrayList<>();
        for (Long magicId : new LinkedHashSet<>(botEye.cardList())) {
            Magic magic = magicId == null ? null : magicLookup.apply(magicId);
            if (magic == null) {
                continue;
            }
            int cost = spellStats.manaCost(magic);
            boolean affordable = cost <= botEye.mana();
            String key = "m" + magic.id;
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("magic", magic.name);
            card.put("manaCost", cost);
            card.put("affordable", affordable);
            if (affordable) {
                card.put("option", key);
                magics.put(key, new LlmCandidates.MagicOption(
                        magic.id, magic.name, cost, describeMagic(magic, cost, parameters)));
            }
            hand.add(card);
        }

        List<BotVisibleObject> enemies = units(botEye, enemySide, ownBase);
        List<BotVisibleObject> allies = units(botEye, botSide, enemyBase);

        Map<String, LlmCandidates.TargetOption> targets = new LinkedHashMap<>();
        int enemySlots = MAX_TARGETS - 1 - DEFENSIVE_OFFSETS.length;
        for (BotVisibleObject enemy : enemies.subList(0, Math.min(enemySlots, enemies.size()))) {
            addTarget(targets, enemy.position(), "enemy " + enemy.type() + " hp " + enemy.hp()
                    + " at " + coordinates(enemy.position()));
        }
        addTarget(targets, enemyBase, "enemy base (player hp " + botEye.enemyPlayerHp() + ") at "
                + coordinates(enemyBase));
        float forward = Math.signum(enemyBase.getX() - ownBase.getX());
        for (float offset : DEFENSIVE_OFFSETS) {
            Vector3 spot = new Vector3(ownBase.getX() + forward * offset, 0, ownBase.getZ());
            addTarget(targets, spot, "defensive spot " + (int) offset + " in front of own base at "
                    + coordinates(spot));
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("side", botSide == Master.LeftPlayer ? "left" : "right");
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("width", GameConfig.WIDTH);
        board.put("depth", GameConfig.HEIGHT);
        board.put("ownBase", point(ownBase));
        board.put("enemyBase", point(enemyBase));
        state.put("board", board);
        state.put("mana", botEye.mana());
        state.put("enemyPlayerHp", botEye.enemyPlayerHp());
        state.put("hand", hand);
        state.put("enemyUnits", summarise(enemies, MAX_STATE_ENEMIES));
        state.put("ownUnits", summarise(allies, MAX_STATE_ALLIES));
        return new LlmCandidates(magics, targets, state);
    }

    /** Living units of one side, nearest to {@code from} first; the player bodies are left out. */
    private static List<BotVisibleObject> units(BotEye botEye, Master side, Vector3 from) {
        return botEye.gameObjectList().stream()
                .filter(object -> object.master() == side)
                .filter(BotVisibleObject::targetable)
                .filter(BotVisibleObject::mob)
                .filter(object -> object.type() != PrefabType.Player)
                .sorted(Comparator.comparingDouble(object -> object.position().distance(from)))
                .toList();
    }

    private static void addTarget(Map<String, LlmCandidates.TargetOption> targets, Vector3 position,
                                  String description) {
        if (targets.size() >= MAX_TARGETS) {
            return;
        }
        targets.put("t" + targets.size(), new LlmCandidates.TargetOption(new Vector3(position), description));
    }

    private static List<Map<String, Object>> summarise(List<BotVisibleObject> objects, int limit) {
        return objects.stream()
                .limit(limit)
                .map(object -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", object.type() == null ? "Unknown" : object.type().name());
                    row.put("hp", object.hp());
                    row.put("x", round(object.position().getX()));
                    row.put("z", round(object.position().getZ()));
                    return row;
                })
                .toList();
    }

    static String describeMagic(Magic magic, int cost, Parameters parameters) {
        StringBuilder text = new StringBuilder(magic.name).append(", ").append(cost).append(" mana: ");
        if (magic instanceof ObjectSummoningMagic summoning) {
            text.append("summons ").append(summoning.summonedQuantity()).append(' ')
                    .append(summoning.summonedPrefab());
            if (summoning.summonsAirborne()) {
                text.append(" (flying)");
            }
        } else {
            double damage = parameters.getValueOrDefault(magic.name, "damage", 0);
            double radius = parameters.getValueOrDefault(magic.name, "radius", 0);
            if (damage > 0) {
                text.append("deals ").append(number(damage)).append(" damage");
                if (radius > 0) {
                    text.append(" in radius ").append(number(radius));
                }
            } else {
                text.append("spell");
            }
        }
        double range = parameters.getValueOrDefault(magic.name, "range", 0);
        if (range > 0) {
            text.append(", max cast range ").append(number(range));
        }
        return text.toString();
    }

    private static Map<String, Object> point(Vector3 position) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", round(position.getX()));
        point.put("z", round(position.getZ()));
        return point;
    }

    private static String coordinates(Vector3 position) {
        return "(" + number(position.getX()) + ", " + number(position.getZ()) + ")";
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String number(double value) {
        double rounded = round(value);
        return rounded == Math.rint(rounded)
                ? Long.toString((long) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }
}
