package com.wordonline.server.preview;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.*;
import com.wordonline.server.game.domain.magic.implement.build.*;
import com.wordonline.server.game.domain.magic.implement.drop.*;
import com.wordonline.server.game.domain.magic.implement.explode.*;
import com.wordonline.server.game.domain.magic.implement.shoot.*;
import com.wordonline.server.game.domain.magic.implement.spawn.*;
import com.wordonline.server.game.domain.object.*;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.physic.*;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.dto.*;
import java.util.*;

/** Catalog situations beyond the representative set. */
final class RemainingPreviewScenarios {

    private final String selectedName;
    private final Map<String, Double> inputs;
    private final Map<String, Class<? extends PrefabInitializer>> prefabs;
    private final Map<String, Magic> magics;
    private final PreviewProperties limits;

    RemainingPreviewScenarios(String selectedName, Map<String, Double> inputs,
          Map<String, Class<? extends PrefabInitializer>> prefabs,
          Map<String, Magic> magics, PreviewProperties limits) {
        this.selectedName = selectedName;
        this.inputs = inputs;
        this.prefabs = prefabs;
        this.magics = magics;
        this.limits = limits;
    }

    private PreviewCapture capture() { return new PreviewCapture(inputs, prefabs, magics, limits); }

    private static final Map<String, String> AREA_UNITS = Map.ofEntries(
            Map.entry("tower", "GroundTower"), Map.entry("bubble_spirit", "BubbleSpirit"),
            Map.entry("fire_spirit", "FireSpirit"), Map.entry("cloud_dragon", "ChainLightning"),
            Map.entry("sea_serpent", "SeaSerpent"), Map.entry("electric_tower", "ElectricTower"),
            Map.entry("dragon_tower", "DragonFlame"), Map.entry("bomb_sprite", "BombSpriteBomb"),
            Map.entry("firework_tower", "FireworkShell"), Map.entry("magma_spirit", "MagmaFist"),
            Map.entry("titan_remnant", "TitanFist"));


    private static Magic magic(String name, PreviewCapture c) { return c.magic(name); }

    Map<String, ObjectNode> captureAll() {
        Map<String, ObjectNode> clips = new LinkedHashMap<>();
        // Each row is a production attack-mode contract, not art-derived inference.
        for (String name : List.of("aqua_archer", "rock_mage", "vine_spirit", "cloud_dragon", "sea_serpent", "evil_ent", "fire_lord_spirit", "electric_tower")) {
            attack(clips, name, false);
            attack(clips, name, true);
        }
        for (String name : List.of("tower", "bubble_spirit")) attack(clips, name, true);
        for (String name : List.of("cannon", "rock_turret", "fire_spirit", "thunder_spirit", "magma_spirit", "wall_golem", "tree_golem", "storm_rider", "storm_stag", "zap_mouse", "chicken_commando", "bomb_sprite", "ember_spirit_swarm", "seed_spirit_swarm", "fire_slime_nest", "rock_slime_nest", "lightning_slime_nest", "wind_slime_nest", "seed_nest", "vine_colony", "titan_remnant", "nature_slime_nest", "pve_water_slime_nest", "dimension_toad", "firework_tower", "crater")) attack(clips, name, false);
        for (String name : List.of("rock_drop", "nature_drop", "lightning_drop", "meteor_shower", "leafair", "magma_explosion", "overgrowth", "razor_gale", "shock_overload", "vine_world", "boulder_strike", "chain_lightning", "tidal_warhead", "tide_call", "vine_fan", "vine_toss", "tornado_strike")) attack(clips, name, false);
        attack(clips, "dragon_tower", false); // Fixed ground-level forward flame, not target-seeking anti-air.

        support(clips);
        secondary(clips);
        return clips;
    }

private void attack(Map<String, ObjectNode> clips, String name, boolean air) {
        if (!selectedName.equals(name)) return;
        try (PreviewCapture c = capture()) {
            float targetX = name.equals("crater") ? 5.5f : 7;
            GameObject target = c.target(Master.RightPlayer, targetX, air ? 2f : 0, 5);
            if (AREA_UNITS.containsKey(name)) {
                if (name.equals("sea_serpent")) {
                    // Collinear victims and an untouched off-axis control explain the beam.
                    c.target(Master.RightPlayer, 5.8f, air ? 2f : 0, 5);
                    c.target(Master.RightPlayer, 8.2f, air ? 2f : 0, 5);
                } else if (name.equals("magma_spirit")) {
                    // OnStartAttacker uses body-edge CombatRange, whereas these
                    // passive fixtures have trigger-only colliders (no body radius).
                    c.target(Master.RightPlayer, 7.65f, 0, 5.7f);
                    c.target(Master.RightPlayer, 7, 0, 4.1f);
                } else if (Set.of("cloud_dragon", "electric_tower", "fire_spirit", "bomb_sprite", "firework_tower").contains(name)) {
                    c.target(Master.RightPlayer, 8.2f, air ? 2f : 0, 5.8f);
                    c.target(Master.RightPlayer, 6.5f, air ? 2f : 0, 6.4f);
                } else {
                    // Small splash: don't enlarge mechanics to fit the display.
                    c.target(Master.RightPlayer, 7.9f, air ? 2f : 0, 5.8f);
                    c.target(Master.RightPlayer, 6.9f, air ? 2f : 0, 6.1f);
                }
            }
            boolean area = Set.of("rock_drop", "nature_drop", "lightning_drop", "meteor_shower", "leafair", "magma_explosion", "overgrowth", "razor_gale", "shock_overload", "vine_world", "tornado_strike").contains(name);
            float duration = name.endsWith("slime_nest") || name.equals("fire_lord_spirit") ? 14 : 9;
            if (name.equals("fire_spirit")) duration = 3.5f;
            c.record(clips, name, air ? "air" : "ground", air ? "공중 공격" : "지상 공격", air ? "Air attack" : "Ground attack", duration,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(area ? targetX : (magics.get(name).getClass().getPackageName().contains("shoot") ? targetX : 4), air && area ? 2f : 0, 5)); });
        }
    }

private void support(Map<String, ObjectNode> clips) {
        for (String name : List.of("life_tree", "bubble_generator")) {
            if (!selectedName.equals(name)) continue;
            try (PreviewCapture c = capture()) {
                GameObject ally = c.target(Master.LeftPlayer, 5, 0, 5);
                ally.getComponent(Mob.class).onDamaged(new AttackInfo(400, ElementType.NONE));
                c.record(clips, name, "support", "아군 회복·보호", "Ally healing or protection", 5,
                        tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(4, 0, 5)); });
            }
        }
        if (selectedName.equals("rallying_totem")) try (PreviewCapture c = capture()) {
            GameObject ally = new GameObject(Master.LeftPlayer, PrefabType.AquaArcher, new Vector3(3, 0, 5), c.context);
            c.record(clips, "rallying_totem", "rally", "집결 이동·고무", "Rally movement and Inspired", 5,
                    tick -> { if (tick == 12) c.cast(magic("rallying_totem", c), new Vector3(6, 0, 5)); });
        }
        if (selectedName.equals("repair_totem")) try (PreviewCapture c = capture()) {
            GameObject ally = new GameObject(Master.LeftPlayer, PrefabType.GroundTower, new Vector3(5, 0, 5), c.context);
            GameObject control = new GameObject(Master.LeftPlayer, PrefabType.GroundTower, new Vector3(10, 0, 5), c.context);
            c.record(clips, "repair_totem", "preserve", "건물 수명 유지·종료 후 소멸", "Building lifetime preserved, resumes after aura", 10,
                    tick -> {
                        if (tick == 12) c.cast(magic("repair_totem", c), new Vector3(4, 0, 5));
                    });
        }
        if (selectedName.equals("mana_well")) try (PreviewCapture c = capture()) {
            c.record(clips, "mana_well", "mana", "마나 충전 속도 증가·종료 후 복귀", "Mana charge rate rises and restores", 6,
                    tick -> {
                        if (tick == 12) c.cast(magic("mana_well", c), new Vector3(5, 0, 5));
                        if (tick == 70) c.context.getActiveGameObjects().stream().filter(o -> o.getType() == PrefabType.ManaWell).findFirst().orElseThrow().destroy();
                    });
        }
        if (selectedName.equals("wind_totem")) try (PreviewCapture c = capture()) {
            GameObject target = c.target(Master.RightPlayer, 6, 0, 5);
            c.record(clips, "wind_totem", "push", "전방 영역 밀치기", "Push a target in the forward volume", 5,
                    tick -> { if (tick == 12) c.cast(magic("wind_totem", c), new Vector3(4, 0, 5)); });
        }
        if (selectedName.equals("shock_trap")) try (PreviewCapture c = capture()) {
            GameObject enemy = c.target(Master.RightPlayer, 6, 0, 5);
            c.record(clips, "shock_trap", "trigger", "적 접근·지연 감전", "Enemy proximity, delayed shock", 5,
                    tick -> { if (tick == 12) c.cast(magic("shock_trap", c), new Vector3(4, 0, 5)); });
        }
        if (selectedName.equals("grass_generator")) try (PreviewCapture c = capture()) {
            GameObject enemy = c.target(Master.RightPlayer, 5.75f, 0, 5);
            GameObject ally = c.target(Master.LeftPlayer, 6, 0, 5);
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(500, ElementType.NONE));
            enemy.getComponent(Mob.class).onDamaged(new AttackInfo(500, ElementType.NONE));
            c.record(clips, "grass_generator", "grass", "잎 필드 확장·속박·현재 서버 회복 대상", "Leaf carpet, snare and current server healing recipients", 10,
                    tick -> { if (tick == 12) c.cast(magic("grass_generator", c), new Vector3(4, 0, 5)); });
        }
        if (selectedName.equals("will_o_wisp")) try (PreviewCapture c = capture()) {
            GameObject controlled = new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(6, 0, 5), c.context);
            GameObject formerAlly = c.target(Master.RightPlayer, 8, 0, 5);
            c.record(clips, "will_o_wisp", "control", "진영 전환·이전 아군 공격", "Change ownership and attack former side", 7,
                    tick -> { if (tick == 12) c.cast(magic("will_o_wisp", c), new Vector3(6, 0, 5)); });
        }
        if (selectedName.equals("spirit_bomb")) try (PreviewCapture c = capture()) {
            GameObject ally = c.target(Master.LeftPlayer, 3, 0, 6);
            GameObject enemy = c.target(Master.RightPlayer, 7, 0, 5);
            c.record(clips, "spirit_bomb", "channel", "아군 체력 흡수·네 번 빔 피해", "Ally HP absorption and four beam ticks", 6,
                    tick -> { if (tick == 12) c.cast(magic("spirit_bomb", c), new Vector3(9, 0, 5)); });
        }
    }

private void secondary(Map<String, ObjectNode> clips) {
        String name = selectedName;
        if (name.equals("leafair")) try (PreviewCapture c = capture()) {
            GameObject ally = c.target(Master.LeftPlayer, 5, 0, 5);
            GameObject control = c.target(Master.LeftPlayer, 10, 0, 5);
            ally.addComponent(new com.wordonline.server.game.domain.object.component.TimedSelfDestroyer(ally, 6));
            control.addComponent(new com.wordonline.server.game.domain.object.component.TimedSelfDestroyer(control, 6));
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(500, ElementType.NONE));
            c.record(clips, name, "ally_recovery", "아군 체력 회복·수명 재충전", "Ally HP healing and lifetime refill", 11,
                    tick -> {
                        if (tick == 50) c.cast(magic(name, c), new Vector3(5, 0, 5));
                    });
        }
        if (Set.of("fire_spirit", "magma_spirit", "cloud_dragon").contains(name)) try (PreviewCapture c = capture()) {
            c.target(Master.RightPlayer, 4.9f, name.equals("cloud_dragon") ? 2f : 0f, 5);
            c.record(clips, name, "aura", "주변 상태 효과", "Nearby status aura", 3,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(4, 0, 5)); });
        }
        if (name.equals("dimension_toad")) try (PreviewCapture c = capture()) {
            new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(6, 0, 5), c.context);
            c.record(clips, name, "panic", "적 유닛 접근·도주·자식 소환", "Approaching enemy unit, panic and child summons", 6,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(4, 0, 5)); });
        }
        if (name.equals("evil_ent")) for (boolean heavy : new boolean[]{false, true}) try (PreviewCapture c = capture()) {
            GameObject victim = c.target(Master.RightPlayer, 8, 0, 5);
            if (heavy) {
                victim.getComponents().remove(victim.getComponent(RigidBody.class));
                victim.getComponents().add(new RigidBody(victim, 100));
            }
            c.record(clips, name, heavy ? "heavy_punch" : "pull", heavy ? "무거운 적·일반 주먹" : "끌어오기·화염 주먹·화상",
                    heavy ? "Heavy victim, punch without pull" : "Grab, pull, fire fist and Burn", 8,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(4, 0, 5)); });
        }
        if (name.equals("sea_serpent")) try (PreviewCapture c = capture()) {
            c.target(Master.RightPlayer, 10, 0, 5);
            c.record(clips, name, "water_trail", "이동 경로 물 필드·젖음", "Movement water trail and Wet", 7,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(3, 0, 5)); });
        }
        Map<String, PrefabType> rockDeaths = Map.of("wall_golem", PrefabType.WallGolem,
                "magma_spirit", PrefabType.MagmaSpirit, "cannon", PrefabType.GroundCannon,
                "tower", PrefabType.GroundTower, "titan_remnant", PrefabType.TitanRemnant);
        if (rockDeaths.containsKey(name)) try (PreviewCapture c = capture()) {
            GameObject unit = new GameObject(Master.LeftPlayer, rockDeaths.get(name), new Vector3(5, 0, 5), c.context);
            c.record(clips, name, "death", "사망·바위 잔해", "Combat death and rock remnant", 4,
                    tick -> { if (tick == 24) unit.getComponent(Mob.class).onDamaged(new AttackInfo(100000, ElementType.NONE)); });
        }
        Map<String, PrefabType> electricDeaths = Map.of("thunder_spirit", PrefabType.ThunderSpirit,
                "storm_rider", PrefabType.StormRider, "zap_mouse", PrefabType.ZapMouse);
        if (electricDeaths.containsKey(name)) try (PreviewCapture c = capture()) {
            List<GameObject> cast = c.cast(magic(name, c), new Vector3(5, 0, 5));
            GameObject unit = cast.stream().filter(object -> object.getType() == electricDeaths.get(name)).findFirst().orElseThrow();
            GameObject absorber = new GameObject(Master.LeftPlayer, PrefabType.ElectricSlime, new Vector3(5.3f, 0, 5), c.context);
            c.record(clips, name, "death_energy", "사망·아군 전기 에너지 흡수", "Combat death and allied energy absorption", 4,
                    tick -> { if (tick == 24) {
                        absorber.setPosition(unit.getPosition().plus(0.1f, 0, 0));
                        unit.getComponent(Mob.class).onDamaged(new AttackInfo(100000, ElementType.NONE));
                    } });
        }
        if (Set.of("rock_mage", "cloud_dragon", "sea_serpent", "chain_lightning").contains(name)) try (PreviewCapture c = capture()) {
            GameObject first = c.target(Master.RightPlayer, 6, 0, 5);
            GameObject second = c.target(Master.RightPlayer, 7, 0, name.equals("sea_serpent") ? 5 : 5.5f);
            GameObject offAxis = name.equals("sea_serpent") ? c.target(Master.RightPlayer, 7, 0, 7) : null;
            c.record(clips, name, "multiple", "복수 대상·공격 방식", "Multiple targets and attack mode", 6,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(name.equals("chain_lightning") ? 7 : 4, 0, 5)); });
        }
        if (name.equals("crater")) try (PreviewCapture c = capture()) {
            c.record(clips, name, "landing", "불씨 착지·중립 화염 필드", "Ember landing and neutral fire fields", 7,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(5, 0, 5)); });
        }
        if (Set.of("overgrowth", "vine_world", "vine_toss", "vine_fan", "vine_colony").contains(name)) try (PreviewCapture c = capture()) {
            float seedX = name.equals("vine_world") || name.equals("overgrowth") ? 6 : 3;
            GameObject seed = new GameObject(Master.LeftPlayer, PrefabType.SeedSpirit, new Vector3(seedX, 0, 5), c.context);
            c.target(Master.RightPlayer, seedX, 0, 5.3f);
            c.record(clips, name, "evolution", "씨앗 정령 진화·진화 후 공격", "Seed evolution and evolved attack", 6,
                    tick -> { if (tick == 12) c.cast(magic(name, c), new Vector3(6, 0, 5)); });
        }
        if (name.equals("tree_golem")) try (PreviewCapture c = capture()) {
            GameObject unit = new GameObject(Master.LeftPlayer, PrefabType.TreeGolem, new Vector3(3, 0, 5), c.context);
            GameObject ally = c.target(Master.LeftPlayer, 4.1f, 0, 5);
            c.target(Master.RightPlayer, 8, 0, 5);
            unit.getComponent(Mob.class).onDamaged(new AttackInfo(400, ElementType.NONE));
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(400, ElementType.NONE));
            c.record(clips, name, "healing_trail", "자가 회복·잎 경로·아군 회복", "Self healing and allied leaf-trail healing", 7, tick -> {});
        }
    }
}
