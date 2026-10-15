package com.wordonline.server.bot.llm;

import java.util.Locale;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * What a unit type does, read from the same parameter rows the game runs on, in words a model
 * reads without the code. The database carries no description text for units, so the hp, hit
 * damage, attack interval, reach and speed are the description.
 *
 * <p>A prefab's parameters live on the game object named like its bean without the
 * {@code _prefab} suffix ({@code wind_spirit_prefab} reads {@code wind_spirit}). A type with no
 * rows reads as zero everywhere and is described as unknown rather than guessed.
 */
final class UnitGuide {

    private static final String PREFAB_SUFFIX = "_prefab";

    private final Parameters parameters;

    UnitGuide(Parameters parameters) {
        this.parameters = parameters;
    }

    record Stats(double hp, double damage, double attackInterval, double attackRange, double speed,
                 boolean flying, double pushForce) {

        boolean known() {
            return hp > 0 || damage > 0;
        }

        double damagePerSecond() {
            return attackInterval > 0 ? damage / attackInterval : damage;
        }

        /** Seconds until a unit this far from a base starts hitting it, or null when it does not move. */
        Double secondsToReach(double distance) {
            double gap = Math.max(0, distance - attackRange);
            if (gap == 0) {
                return 0.0;
            }
            return speed > 0 ? gap / speed : null;
        }
    }

    Stats stats(PrefabType type) {
        if (type == null) {
            return new Stats(0, 0, 0, 0, 0, false, 0);
        }
        String object = objectName(type);
        return new Stats(
                value(object, "hp"),
                value(object, "damage"),
                value(object, "attack_interval"),
                value(object, "attack_range"),
                value(object, "speed"),
                value(object, "spawn_height") > 0,
                value(object, "push_force"));
    }

    /** One line: "flying, 50 hp, hits for 300 every 3s (100 dps) at range 1.5, speed 1.5". */
    String describe(PrefabType type) {
        Stats stats = stats(type);
        if (!stats.known()) {
            return "no stats known";
        }
        StringBuilder text = new StringBuilder();
        text.append(stats.flying() ? "flying" : "ground").append(", ").append(number(stats.hp())).append(" hp");
        if (stats.damage() > 0) {
            text.append(", hits for ").append(number(stats.damage()));
            if (stats.attackInterval() > 0) {
                text.append(" every ").append(number(stats.attackInterval())).append('s')
                        .append(" (").append(number(stats.damagePerSecond())).append(" dps)");
            }
            if (stats.attackRange() > 0) {
                text.append(" at range ").append(number(stats.attackRange()));
            }
        }
        text.append(stats.speed() > 0 ? ", speed " + number(stats.speed()) : ", does not move");
        if (stats.pushForce() > 0) {
            text.append(", pushes enemy units back");
        }
        return text.toString();
    }

    /**
     * What the unit does on the field, in words: whether it walks or stays put, what it hits and
     * how hard, and whether it pushes. "flies toward the enemy base at speed 1.5, fighting what it
     * meets; 50 hp; hits for 300 every 3s (100 dps) at range 1.5".
     */
    String role(PrefabType type) {
        Stats stats = stats(type);
        if (!stats.known()) {
            return "no stats known";
        }
        StringBuilder text = new StringBuilder();
        if (stats.speed() > 0) {
            text.append(stats.flying() ? "flies" : "walks").append(" toward the enemy base at speed ")
                    .append(number(stats.speed())).append(", fighting what it meets");
        } else {
            text.append("stays where it is placed and never moves, so it only acts on enemies that come to it");
        }
        text.append("; ").append(number(stats.hp())).append(" hp");
        if (stats.damage() > 0) {
            text.append("; hits for ").append(number(stats.damage()));
            if (stats.attackInterval() > 0) {
                text.append(" every ").append(number(stats.attackInterval())).append("s (")
                        .append(number(stats.damagePerSecond())).append(" dps)");
            }
            if (stats.attackRange() > 0) {
                text.append(" at range ").append(number(stats.attackRange()));
            }
        }
        if (stats.pushForce() > 0) {
            text.append("; blows enemy units back");
        }
        return text.toString();
    }

    /** How far from where it stands the unit acts: its attack range or its push reach, whichever is longer. */
    double reach(PrefabType type) {
        if (type == null) {
            return 0;
        }
        String object = objectName(type);
        return Math.max(value(object, "attack_range"), value(object, "push_range_x"));
    }

    static String objectName(PrefabType type) {
        String bean = type.getBeanName();
        return bean.endsWith(PREFAB_SUFFIX) ? bean.substring(0, bean.length() - PREFAB_SUFFIX.length()) : bean;
    }

    private double value(String object, String parameter) {
        return parameters.getValueOrDefault(object, parameter, 0);
    }

    static String number(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        return rounded == Math.rint(rounded)
                ? Long.toString((long) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }
}
