package com.wordonline.server.game.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.wordonline.server.game.preview.MagicScenarioPreviewTest.Capture;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit scenario catalog; discovery wires production constructors, never spell semantics. */
class RemainingMagicPreviewTest {
    private String selectedName;
    private final org.assertj.core.api.SoftAssertions attackChecks = new org.assertj.core.api.SoftAssertions();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Class<?>> MAGICS = beans(Magic.class, "magic.implement");
    private static final Map<String, Class<?>> PREFABS = beans(PrefabInitializer.class, "object.prefab.implement");
    private static final Set<String> PREVIOUS = Set.of("fire_shot", "rock_golem", "wind_spirit", "towerback",
            "healing_totem", "lightning_explosion", "sand_storm", "frenzy_totem", "earth_call",
            "water_shot", "lightning_shot", "wind_blade", "rock_rolling", "fire_drop", "wind_drop",
            "water_explosion", "wind_explosion", "rock_blast", "mini_rock_swarm", "thunder_bird_swarm", "water_slime_swarm");

    static java.util.stream.Stream<String> remainingNames() {
        return MAGICS.keySet().stream().filter(name -> !PREVIOUS.contains(name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("remainingNames")
    void recordsRemainingMechanicsDeterministically(String name) throws Exception {
        selectedName = name;
        Map<String, ObjectNode> first = captureAll(), second = captureAll();
        assertThat(first.keySet()).as("Explicit scenario registration: %s", name).containsExactly(name);
        assertThat(difference(first.get(name), second.get(name), name)).as("Determinism").isNull();
        if (Boolean.getBoolean("preview.export")) {
            Path directory = Path.of("build/previews/scenarios");
            Files.createDirectories(directory);
            for (var entry : first.entrySet()) Files.writeString(directory.resolve(entry.getKey() + ".json"),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(entry.getValue()) + "\n");
        }
    }

    private static String difference(JsonNode a, JsonNode b, String path) {
        if (Objects.equals(a, b)) return null;
        if (a == null || b == null || a.isValueNode() || b.isValueNode()) return path + ": " + a + " != " + b;
        if (a.isArray() && a.size() != b.size()) return path + " sizes: " + a.size() + " != " + b.size();
        if (a.isArray()) {
            for (int i = 0; i < a.size(); i++) { String d = difference(a.get(i), b.get(i), path + "[" + i + "]"); if (d != null) return d; }
        } else {
            var names = a.fieldNames();
            while (names.hasNext()) { String key = names.next(); String d = difference(a.get(key), b.get(key), path + "." + key); if (d != null) return d; }
        }
        return path;
    }

    static double fixtureValue(String object, String key) {
        // Explicit illustrative values, not a zero/default fallback for unknown reads.
        if (key.equals("damage") && Set.of("life_tree", "healing_totem").contains(object)) return -80;
        if (key.equals("duration") && Set.of("ground_cannon", "ground_tower", "rock_turret").contains(object)) return 3;
        if (key.equals("speed") && Set.of("storm_stag", "zap_mouse").contains(object)) return 4;
        if (key.equals("detection_range") && Set.of("storm_stag", "zap_mouse").contains(object)) return 0.6;
        if (key.equals("sub_attack_range") && object.equals("evil_ent")) return 6;
        if (key.equals("attack_offset")) return 3;
        if (key.equals("radius") && Set.of("meteor_shower", "magma_explosion", "overgrowth", "shock_overload", "razor_gale", "leafair").contains(object)) return 2;
        if (key.equals("duration") && object.equals("repair_totem")) return 5;
        // This initializer reads an integer interval: 0.8 would truncate to zero
        // and TimedBehaviorMob's production catch-up loop would never terminate.
        if (key.equals("attack_interval") && object.equals("vine_colony")) return 1;
        return switch (key) {
            case "hp" -> 1000;
            case "mass" -> 10;
            case "radius" -> 0.4;
            case "damage" -> 100;
            case "speed" -> object.contains("shot") || Set.of("chain_lightning", "boulder_strike", "tide_call", "will_o_wisp", "tidal_warhead").contains(object) ? 6 : 2;
            case "attack_interval", "heal_interval", "sub_attack_interval", "spawn_interval" -> 0.8;
            case "attack_range", "range", "effect_radius", "chain_radius" -> 3;
            case "duration" -> 8;
            case "sub_speed" -> 1.5;
            case "sub_damage", "chain_damage", "heal_amount" -> 80;
            case "sub_attack_range" -> 1;
            case "buff_duration" -> 2;
            case "z_force", "push_force" -> 5;
            case "projectile_speed" -> 8;
            case "quantity", "chain_count", "vine_count" -> 3;
            case "min_damage" -> 20;
            case "max_mana" -> 100;
            case "acceleration" -> 5;
            case "detection_range", "pull_mass_limit" -> 20;
            case "panic_duration" -> 0.8;
            case "spawn_height" -> 5;
            case "fall_gravity" -> 20;
            case "chain_lightning_cooldown", "trigger_delay", "stun_duration" -> 1;
            case "beam_width" -> 0.4;
            case "push_range_x", "push_range_y", "push_range_z" -> 6;
            case "burn_duration", "wet_duration", "snare_duration", "leaf_field_heal_duration", "fever_duration" -> 2;
            case "burn_total_damage" -> 80;
            case "snare_fire_damage", "leaf_field_heal_amount", "wet_nature_heal" -> 20;
            case "snare_slow_percent" -> 0.5;
            case "shock_stun_duration", "shock_refresh_duration", "sandstorm_effect_duration" -> 1;
            case "sandstorm_effect_damage" -> 30;
            case "building_snare_heal" -> 10;
            case "vine_spacing" -> 1;
            case "vine_spawn_interval" -> 0.12;
            default -> throw new AssertionError("Missing remaining-preview parameter: " + object + "." + key);
        };
    }

    static PrefabInitializer initializer(PrefabType type, Parameters parameters) {
        Class<?> bean = PREFABS.get(type.getBeanName());
        if (bean == null) throw new AssertionError("Missing production initializer: " + type);
        return (PrefabInitializer) construct(bean, parameters);
    }

    private static Map<String, Class<?>> beans(Class<?> base, String suffix) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(base));
        Map<String, Class<?>> found = new TreeMap<>();
        for (var candidate : scanner.findCandidateComponents("com.wordonline.server.game.domain." + suffix)) {
            try {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                Component annotation = type.getAnnotation(Component.class);
                if (annotation != null) found.put(annotation.value(), type);
            } catch (ClassNotFoundException e) { throw new AssertionError(e); }
        }
        return found;
    }

    private static Object construct(Class<?> type, Parameters parameters) {
        try {
            var constructor = type.getConstructors()[0];
            Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(dependency -> {
                if (dependency == Parameters.class) return parameters;
                if (dependency == VineTossMagic.class) return new VineTossMagic();
                throw new AssertionError("Unsupported fixture dependency: " + type + " / " + dependency);
            }).toArray();
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) { throw new AssertionError(type.getName(), e); }
    }

    private static Magic magic(String name, Capture c) {
        Class<?> type = MAGICS.get(name);
        if (type == null) throw new AssertionError("Unknown production magic: " + name);
        return (Magic) construct(type, c.parameters);
    }

    private Map<String, ObjectNode> captureAll() {
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
        attackChecks.assertAll();
        support(clips);
        secondary(clips);
        return clips;
    }

    private void attack(Map<String, ObjectNode> clips, String name, boolean air) {
        if (!selectedName.equals(name)) return;
        try (Capture c = new Capture(true)) {
            float targetX = name.equals("crater") ? 5.5f : 7;
            GameObject target = c.target(Master.RightPlayer, targetX, air ? 2f : 0, 5);
            boolean area = Set.of("rock_drop", "nature_drop", "lightning_drop", "meteor_shower", "leafair", "magma_explosion", "overgrowth", "razor_gale", "shock_overload", "vine_world", "tornado_strike").contains(name);
            float duration = name.endsWith("slime_nest") || name.equals("fire_lord_spirit") ? 14 : 9;
            if (name.equals("fire_spirit")) duration = 3.5f;
            c.record(clips, name, air ? "air" : "ground", air ? "공중 공격" : "지상 공격", air ? "Air attack" : "Ground attack", duration,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(area ? targetX : (MAGICS.get(name).getPackageName().contains("shoot") ? targetX : 4), air && area ? 2f : 0, 5)); });
            attackChecks.assertThat(c.hp(target)).as("%s %s attack damage", name, air ? "air" : "ground").isLessThan(10000);
            Map<String, String> status = Map.ofEntries(Map.entry("vine_spirit", "Snared"),
                    Map.entry("fire_spirit", "Burn"), Map.entry("magma_spirit", "Burn"), Map.entry("lightning_drop", "Shock"),
                    Map.entry("shock_overload", "Shock"), Map.entry("storm_stag", "Overcharge"));
            if (status.containsKey(name)) assertThat(c.seenEffects).as(name).contains(status.get(name));
            if (name.equals("fire_lord_spirit")) {
                assertThat(c.seenTypes).contains("FireChildSpirit");
                assertThat(c.seenProjectiles).contains("FireShot");
                assertThat(firstTime(c.lastScenario, "create", "FireChildSpirit")).isGreaterThanOrEqualTo(5.5);
            }
            if (name.equals("dimension_toad")) assertThat(c.seenTypes).contains("FireTadpole", "LightningTadpole");
            if (name.equals("rock_mage")) {
                assertThat(c.seenProjectiles).contains("RockShot");
                boolean doubled = false;
                for (JsonNode frame : c.lastScenario.path("frames")) {
                    Map<String, Integer> counts = new HashMap<>();
                    for (JsonNode shot : frame.path("objects").path("projectile")) if (shot.path("type").asText().equals("RockShot"))
                        counts.merge(shot.path("start").path("id").asText() + "/" + shot.path("end").path("id").asText(), 1, Integer::sum);
                    doubled |= counts.containsValue(2);
                }
                assertThat(doubled).as("Two shots from one mage to a single target").isTrue();
            }
            if (name.equals("storm_stag")) {
                assertThat(c.seenEffects).contains("StormStagCharge2", "StormStagCharge3", "StormStagCharge4", "Panic");
                assertThat(c.seenProjectiles).contains("ElectricShot");
            }
            if (name.equals("firework_tower")) {
                assertThat(firstTime(c.lastScenario, "create", "FireworkShell"))
                        .isGreaterThan(firstTime(c.lastScenario, "projectile", "FireworkShell") + 0.4);
            }
            if (name.equals("shock_overload")) assertThat(c.seenProjectiles).contains("ShockOverloadSecondary");
        }
    }

    private void support(Map<String, ObjectNode> clips) {
        for (String name : List.of("life_tree", "bubble_generator")) {
            if (!selectedName.equals(name)) continue;
            try (Capture c = new Capture(true)) {
                GameObject ally = c.target(Master.LeftPlayer, 5, 0, 5);
                ally.getComponent(Mob.class).onDamaged(new AttackInfo(400, ElementType.NONE));
                c.record(clips, name, "support", "아군 회복·보호", "Ally healing or protection", 5,
                        tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
                if (name.equals("life_tree")) assertThat(c.hp(ally)).isGreaterThan(9600);
                else assertThat(c.seenEffects).contains("Bubble");
            }
        }
        if (selectedName.equals("rallying_totem")) try (Capture c = new Capture(true)) {
            GameObject ally = new GameObject(Master.LeftPlayer, PrefabType.AquaArcher, new Vector3(3, 0, 5), c.context);
            c.record(clips, "rallying_totem", "rally", "집결 이동·고무", "Rally movement and Inspired", 5,
                    tick -> { if (tick == 12) magic("rallying_totem", c).run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
            assertThat(ally.getPosition().getX()).isGreaterThan(3);
            assertThat(c.seenEffects).contains("Inspired");
        }
        if (selectedName.equals("repair_totem")) try (Capture c = new Capture(true)) {
            GameObject ally = new GameObject(Master.LeftPlayer, PrefabType.GroundTower, new Vector3(5, 0, 5), c.context);
            GameObject control = new GameObject(Master.LeftPlayer, PrefabType.GroundTower, new Vector3(10, 0, 5), c.context);
            boolean[] preserved = {false};
            c.record(clips, "repair_totem", "preserve", "건물 수명 유지·종료 후 소멸", "Building lifetime preserved, resumes after aura", 10,
                    tick -> {
                        if (tick == 12) magic("repair_totem", c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5));
                        if (tick == 85) preserved[0] = !ally.isDestroyed() && control.isDestroyed();
                    });
            assertThat(preserved[0]).isTrue();
            assertThat(ally.isDestroyed()).isTrue();
            assertThat(c.hp(ally)).isEqualTo(1000);
        }
        if (selectedName.equals("mana_well")) try (Capture c = new Capture(true)) {
            c.record(clips, "mana_well", "mana", "마나 충전 속도 증가·종료 후 복귀", "Mana charge rate rises and restores", 6,
                    tick -> {
                        if (tick == 12) magic("mana_well", c).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
                        if (tick == 70) c.context.getActiveGameObjects().stream().filter(o -> o.getType() == PrefabType.ManaWell).findFirst().orElseThrow().destroy();
                    });
            List<Double> rates = new ArrayList<>();
            c.lastScenario.path("frames").forEach(f -> rates.add(f.path("manaRate").asDouble()));
            assertThat(Collections.max(rates)).isGreaterThan(rates.get(0));
            assertThat(rates.get(rates.size() - 1)).isEqualTo(rates.get(0));
        }
        if (selectedName.equals("wind_totem")) try (Capture c = new Capture(true)) {
            GameObject target = c.target(Master.RightPlayer, 6, 0, 5);
            c.record(clips, "wind_totem", "push", "전방 영역 밀치기", "Push a target in the forward volume", 5,
                    tick -> { if (tick == 12) magic("wind_totem", c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
            assertThat(target.getPosition().getX()).isGreaterThan(6);
        }
        if (selectedName.equals("shock_trap")) try (Capture c = new Capture(true)) {
            GameObject enemy = c.target(Master.RightPlayer, 6, 0, 5);
            c.record(clips, "shock_trap", "trigger", "적 접근·지연 감전", "Enemy proximity, delayed shock", 5,
                    tick -> { if (tick == 12) magic("shock_trap", c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
            assertThat(c.seenEffects).contains("Shock");
        }
        if (selectedName.equals("grass_generator")) try (Capture c = new Capture(true)) {
            GameObject enemy = c.target(Master.RightPlayer, 5.75f, 0, 5);
            GameObject ally = c.target(Master.LeftPlayer, 6, 0, 5);
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(500, ElementType.NONE));
            enemy.getComponent(Mob.class).onDamaged(new AttackInfo(500, ElementType.NONE));
            c.record(clips, "grass_generator", "grass", "잎 필드 확장·속박·현재 서버 회복 대상", "Leaf carpet, snare and current server healing recipients", 10,
                    tick -> { if (tick == 12) magic("grass_generator", c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
            assertThat(c.seenTypes).contains("LeafField");
            // Owned LeafField currently uses enemy-only EffectProvider; preserve, don't fix gameplay here.
            assertThat(c.hp(ally)).isEqualTo(9500);
            assertThat(c.hp(enemy)).isGreaterThan(9500);
            assertThat(c.seenEffects).contains("Snared");
        }
        if (selectedName.equals("will_o_wisp")) try (Capture c = new Capture(true)) {
            GameObject controlled = new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(6, 0, 5), c.context);
            GameObject formerAlly = c.target(Master.RightPlayer, 8, 0, 5);
            c.record(clips, "will_o_wisp", "control", "진영 전환·이전 아군 공격", "Change ownership and attack former side", 7,
                    tick -> { if (tick == 12) magic("will_o_wisp", c).run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
            assertThat(controlled.getMaster()).isEqualTo(Master.LeftPlayer);
            assertThat(c.hp(formerAlly)).isLessThan(10000);
        }
        if (selectedName.equals("spirit_bomb")) try (Capture c = new Capture(true)) {
            GameObject ally = c.target(Master.LeftPlayer, 3, 0, 6);
            GameObject enemy = c.target(Master.RightPlayer, 7, 0, 5);
            c.record(clips, "spirit_bomb", "channel", "아군 체력 흡수·네 번 빔 피해", "Ally HP absorption and four beam ticks", 6,
                    tick -> { if (tick == 12) magic("spirit_bomb", c).run(c.context, Master.LeftPlayer, new Vector3(9, 0, 5)); });
            assertThat(c.hp(ally)).isEqualTo(5000);
            assertThat(c.hp(enemy)).isEqualTo(6500);
            assertThat(c.seenProjectiles).contains("ElectricAbsorb", "SpiritBombBeam");
            List<Double> beamTimes = new ArrayList<>();
            for (JsonNode frame : c.lastScenario.path("frames")) for (JsonNode shot : frame.path("objects").path("projectile"))
                if (shot.path("type").asText().equals("SpiritBombBeam")) {
                    beamTimes.add(frame.path("time").asDouble());
                    assertThat(shot.path("width").asDouble()).isPositive();
                    assertThat(shot.path("duration").asDouble()).isGreaterThanOrEqualTo(1);
                }
            assertThat(beamTimes).hasSize(4);
            for (int i = 1; i < beamTimes.size(); i++) assertThat(beamTimes.get(i) - beamTimes.get(i - 1)).isBetween(0.99, 1.06);
        }
    }

    private void secondary(Map<String, ObjectNode> clips) {
        String name = selectedName;
        if (name.equals("leafair")) try (Capture c = new Capture(true)) {
            GameObject ally = c.target(Master.LeftPlayer, 5, 0, 5);
            GameObject control = c.target(Master.LeftPlayer, 10, 0, 5);
            ally.addComponent(new com.wordonline.server.game.domain.object.component.TimedSelfDestroyer(ally, 6));
            control.addComponent(new com.wordonline.server.game.domain.object.component.TimedSelfDestroyer(control, 6));
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(500, ElementType.NONE));
            boolean[] recovered = {false};
            c.record(clips, name, "ally_recovery", "아군 체력 회복·수명 재충전", "Ally HP healing and lifetime refill", 11,
                    tick -> {
                        if (tick == 50) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
                        if (tick == 130) recovered[0] = !ally.isDestroyed() && control.isDestroyed();
                    });
            assertThat(c.hp(ally)).isGreaterThan(9500);
            assertThat(recovered[0]).as("Recovered ally outlives control").isTrue();
            assertThat(ally.isDestroyed()).isTrue();
        }
        if (Set.of("fire_spirit", "magma_spirit", "cloud_dragon").contains(name)) try (Capture c = new Capture(true)) {
            c.target(Master.RightPlayer, 4.9f, name.equals("cloud_dragon") ? 2f : 0f, 5);
            c.record(clips, name, "aura", "주변 상태 효과", "Nearby status aura", 3,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
            assertThat(c.seenEffects).contains(name.equals("cloud_dragon") ? "Wet" : "Burn");
        }
        if (name.equals("dimension_toad")) try (Capture c = new Capture(true)) {
            new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(6, 0, 5), c.context);
            c.record(clips, name, "panic", "적 유닛 접근·도주·자식 소환", "Approaching enemy unit, panic and child summons", 6,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
            assertThat(c.seenEffects).contains("Panic");
            assertThat(c.seenTypes).contains("FireTadpole", "LightningTadpole");
        }
        if (name.equals("evil_ent")) for (boolean heavy : new boolean[]{false, true}) try (Capture c = new Capture(true)) {
            GameObject victim = c.target(Master.RightPlayer, 8, 0, 5);
            if (heavy) {
                victim.getComponents().remove(victim.getComponent(RigidBody.class));
                victim.getComponents().add(new RigidBody(victim, 100));
            }
            c.record(clips, name, heavy ? "heavy_punch" : "pull", heavy ? "무거운 적·일반 주먹" : "끌어오기·화염 주먹·화상",
                    heavy ? "Heavy victim, punch without pull" : "Grab, pull, fire fist and Burn", 8,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
            if (heavy) {
                assertThat(c.seenProjectiles).contains("EvilEntPunchArm").doesNotContain("EvilEntGrabArm", "EvilEntFireFist");
            } else {
                assertThat(victim.getPosition().getX()).isLessThan(8);
                assertThat(c.seenProjectiles).contains("EvilEntGrabArm", "EvilEntFireFist");
                assertThat(c.seenEffects).contains("Burn");
            }
        }
        if (name.equals("sea_serpent")) try (Capture c = new Capture(true)) {
            c.target(Master.RightPlayer, 10, 0, 5);
            c.record(clips, name, "water_trail", "이동 경로 물 필드·젖음", "Movement water trail and Wet", 7,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(3, 0, 5)); });
            assertThat(c.seenTypes).contains("WaterField");
        }
        Map<String, PrefabType> rockDeaths = Map.of("wall_golem", PrefabType.WallGolem,
                "magma_spirit", PrefabType.MagmaSpirit, "cannon", PrefabType.GroundCannon,
                "tower", PrefabType.GroundTower, "titan_remnant", PrefabType.TitanRemnant);
        if (rockDeaths.containsKey(name)) try (Capture c = new Capture(true)) {
            GameObject unit = new GameObject(Master.LeftPlayer, rockDeaths.get(name), new Vector3(5, 0, 5), c.context);
            c.record(clips, name, "death", "사망·바위 잔해", "Combat death and rock remnant", 4,
                    tick -> { if (tick == 24) unit.getComponent(Mob.class).onDamaged(new AttackInfo(100000, ElementType.NONE)); });
            assertThat(unit.isDestroyed()).isTrue();
            assertThat(c.seenTypes).contains(Set.of("wall_golem", "magma_spirit", "titan_remnant").contains(name) ? "MediumRockRemnant" : "RockRemnant");
        }
        Map<String, PrefabType> electricDeaths = Map.of("thunder_spirit", PrefabType.ThunderSpirit,
                "storm_rider", PrefabType.StormRider, "zap_mouse", PrefabType.ZapMouse);
        if (electricDeaths.containsKey(name)) try (Capture c = new Capture(true)) {
            GameObject unit = new GameObject(Master.LeftPlayer, electricDeaths.get(name), new Vector3(5, 0, 5), c.context);
            GameObject absorber = new GameObject(Master.LeftPlayer, PrefabType.ElectricSlime, new Vector3(5.3f, 0, 5), c.context);
            c.record(clips, name, "death_energy", "사망·아군 전기 에너지 흡수", "Combat death and allied energy absorption", 4,
                    tick -> { if (tick == 24) {
                        absorber.setPosition(unit.getPosition().plus(0.1f, 0, 0));
                        unit.getComponent(Mob.class).onDamaged(new AttackInfo(100000, ElementType.NONE));
                    } });
            assertThat(c.seenProjectiles).contains("ElectricAbsorb");
            assertThat(c.seenEffects).contains("Overcharge");
        }
        if (Set.of("rock_mage", "cloud_dragon", "sea_serpent", "chain_lightning").contains(name)) try (Capture c = new Capture(true)) {
            GameObject first = c.target(Master.RightPlayer, 6, 0, 5);
            GameObject second = c.target(Master.RightPlayer, 7, 0, name.equals("sea_serpent") ? 5 : 5.5f);
            GameObject offAxis = name.equals("sea_serpent") ? c.target(Master.RightPlayer, 7, 0, 7) : null;
            c.record(clips, name, "multiple", "복수 대상·공격 방식", "Multiple targets and attack mode", 6,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(name.equals("chain_lightning") ? 7 : 4, 0, 5)); });
            assertThat(c.hp(first)).isLessThan(10000);
            assertThat(c.hp(second)).isLessThan(10000);
            if (offAxis != null) assertThat(c.hp(offAxis)).isEqualTo(10000);
            if (name.equals("cloud_dragon")) {
                assertThat(c.seenProjectiles).contains("WaterShot");
                assertThat(c.seenTypes).contains("ChainLightning");
            }
        }
        if (name.equals("crater")) try (Capture c = new Capture(true)) {
            c.record(clips, name, "landing", "불씨 착지·중립 화염 필드", "Ember landing and neutral fire fields", 7,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5)); });
            assertThat(c.seenTypes).contains("FireField");
        }
        if (Set.of("overgrowth", "vine_world", "vine_toss", "vine_fan", "vine_colony").contains(name)) try (Capture c = new Capture(true)) {
            float seedX = name.equals("vine_world") || name.equals("overgrowth") ? 6 : 3;
            GameObject seed = new GameObject(Master.LeftPlayer, PrefabType.SeedSpirit, new Vector3(seedX, 0, 5), c.context);
            c.target(Master.RightPlayer, seedX, 0, 5.3f);
            c.record(clips, name, "evolution", "씨앗 정령 진화·진화 후 공격", "Seed evolution and evolved attack", 6,
                    tick -> { if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
            assertThat(seed.isDestroyed()).isTrue();
            assertThat(c.seenTypes).contains(name.equals("overgrowth") ? "TreeGolem" : "VineSpirit");
        }
        if (name.equals("tree_golem")) try (Capture c = new Capture(true)) {
            GameObject unit = new GameObject(Master.LeftPlayer, PrefabType.TreeGolem, new Vector3(3, 0, 5), c.context);
            GameObject ally = c.target(Master.LeftPlayer, 4.1f, 0, 5);
            c.target(Master.RightPlayer, 8, 0, 5);
            unit.getComponent(Mob.class).onDamaged(new AttackInfo(400, ElementType.NONE));
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(400, ElementType.NONE));
            c.record(clips, name, "healing_trail", "자가 회복·잎 경로·아군 회복", "Self healing and allied leaf-trail healing", 7, tick -> {});
            assertThat(c.hp(unit)).isGreaterThan(600);
            assertThat(c.hp(ally)).isGreaterThan(9600);
            assertThat(c.seenTypes).contains("LeafField");
        }
        if ((MAGICS.get(name).getPackageName().contains("spawn") || MAGICS.get(name).getPackageName().contains("build"))
                && !Set.of("rallying_totem", "tornado_strike").contains(name)
                && !rockDeaths.containsKey(name) && !electricDeaths.containsKey(name)) try (Capture c = new Capture(true)) {
            GameObject[] source = {null};
            c.record(clips, name, "combat_death", "소환체 사망·서버 부가효과", "Summon combat death and server aftermath", 5,
                    tick -> {
                        if (tick == 12) magic(name, c).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
                        if (tick == 25) {
                            source[0] = c.context.getActiveGameObjects().stream().filter(o -> o.getMaster() == Master.LeftPlayer
                                    && o.getType() != PrefabType.Player && o.hasComponent(Mob.class)).findFirst().orElseThrow();
                            source[0].getComponent(Mob.class).onDamaged(new AttackInfo(100000, ElementType.NONE));
                        }
                    });
            assertThat(source[0].isDestroyed()).as("Combat death: %s", name).isTrue();
        }
    }

    private static double firstTime(ObjectNode scenario, String category, String type) {
        for (JsonNode frame : scenario.path("frames")) for (JsonNode item : frame.path("objects").path(category))
            if (item.path("type").asText().equals(type)) return frame.path("time").asDouble();
        throw new AssertionError("Missing " + category + ": " + type);
    }
}
