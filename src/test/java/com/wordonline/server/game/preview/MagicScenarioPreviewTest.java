package com.wordonline.server.game.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.implement.build.*;
import com.wordonline.server.game.domain.magic.implement.drop.FrenzyMagic;
import com.wordonline.server.game.domain.magic.implement.explode.*;
import com.wordonline.server.game.domain.magic.implement.shoot.FireShotMagic;
import com.wordonline.server.game.domain.magic.implement.spawn.*;
import com.wordonline.server.game.domain.object.*;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.*;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.domain.object.prefab.implement.drop.FrenzyTotemPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.explode.ElectricExplodePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.fire.FireShotPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.*;
import com.wordonline.server.game.domain.object.prefab.implement.rock.*;
import com.wordonline.server.game.domain.object.prefab.implement.wind.WindSpiritPrefabInitializer;
import com.wordonline.server.game.dto.*;
import com.wordonline.server.game.service.*;
import com.wordonline.server.game.service.system.*;
import com.wordonline.server.game.util.SimplePhysics;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.*;
import java.util.*;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Offline representative scenarios. Only targets/parameter storage are fixtures, not spell logic. */
class MagicScenarioPreviewTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final float DT = 0.05f;

    @Test
    void recordsRepresentativeMechanicsDeterministically() throws Exception {
        Map<String, ObjectNode> first = captureAll();
        var second = captureAll();
        for (String magic : first.keySet())
            assertThat(first.get(magic).equals(second.get(magic)))
                    .as("Determinism: %s %s", magic, difference(first.get(magic), second.get(magic), "")) .isTrue();
        if (Boolean.getBoolean("preview.export")) {
            Path directory = Path.of("build/previews/scenarios");
            Files.createDirectories(directory);
            for (var entry : first.entrySet())
                Files.writeString(directory.resolve(entry.getKey() + ".json"),
                        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(entry.getValue()) + "\n");
        }
    }

    private String difference(JsonNode a, JsonNode b, String path) {
        if (Objects.equals(a, b)) return "";
        if (a != null && b != null && a.isContainerNode() && b.isContainerNode()) {
            if (a.isArray()) {
                for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
                    String result = difference(a.get(i), b.get(i), path + "/" + i);
                    if (!result.isEmpty()) return result;
                }
            } else {
                var names = a.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    String result = difference(a.get(name), b.get(name), path + "/" + name);
                    if (!result.isEmpty()) return result;
                }
            }
        }
        return path + ": " + a + " != " + b;
    }

    private Map<String, ObjectNode> captureAll() {
        Map<String, ObjectNode> clips = new LinkedHashMap<>();
        try (Capture c = new Capture()) {
            GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
            c.record(clips, "rock_golem", "ground", "지상 공격 · 밀치기", "Ground attack · knockback", 4,
                    tick -> { if (tick == 12) new RockGolemMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5)); });
            assertThat(c.hp(target)).isLessThan(1000);
            assertThat(target.getPosition().getX()).isGreaterThan(6.5f);
            assertThat(c.seenEffects).contains("Knockback");
        }
        try (Capture c = new Capture()) {
            new RockGolemMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
            GameObject golem = c.pending(PrefabType.RockGolem);
            c.record(clips, "rock_golem", "death", "사망 후 바위 잔해", "Rock remnant on death", 3,
                    tick -> { if (tick == 20) golem.getComponent(Mob.class).onDamaged(new AttackInfo(10000, com.wordonline.server.game.domain.magic.ElementType.NONE)); });
            assertThat(golem.isDestroyed()).isTrue();
            assertThat(c.seenTypes).contains("RockRemnant");
        }
        try (Capture c = new Capture()) {
            GameObject target = c.target(Master.RightPlayer, 7, 2, 5);
            GameObject adjacent = c.target(Master.RightPlayer, 7.7f, 2, 5);
            c.record(clips, "wind_spirit", "air", "공중 돌진 · 자폭", "Air charge · self-destruct", 4,
                    tick -> { if (tick == 12) new WindSpiritMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5)); });
            assertThat(c.hp(target)).isLessThan(1000);
            assertThat(c.hp(adjacent)).isLessThan(1000);
            assertThat(c.context.getActiveGameObjects().stream().noneMatch(o -> o.getType() == PrefabType.WindSpirit)).isTrue();
        }
        for (boolean air : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                GameObject target = c.target(Master.RightPlayer, air ? 7 : 6, air ? 2 : 0, 5);
                GameObject adjacent = air ? c.target(Master.RightPlayer, 7.6f, 2, 5) : null;
                c.record(clips, "towerback", air ? "air" : "ground", air ? "공중 공격 · 포탄 범위 피해" : "지상 공격", air ? "Air attack · splash" : "Ground attack", 4,
                        tick -> { if (tick == 12) new TowerbackMagic().run(c.context, Master.LeftPlayer, new Vector3(4.5f, 0, 5)); });
                assertThat(c.hp(target)).isLessThan(1000);
                if (air) {
                    assertThat(c.seenProjectiles).contains("RockShot");
                    assertThat(c.hp(adjacent)).isLessThan(1000);
                } else assertThat(c.seenProjectiles).doesNotContain("RockShot");
            }
        }
        try (Capture c = new Capture()) {
            GameObject ally = c.target(Master.LeftPlayer, 6, 0, 5);
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(500, com.wordonline.server.game.domain.magic.ElementType.NONE));
            c.record(clips, "healing_totem", "heal", "부상당한 아군 회복", "Heal an injured ally", 4,
                    tick -> { if (tick == 12) new HealingTotemMagic().run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5)); });
            assertThat(c.hp(ally)).isGreaterThan(500);
        }
        for (boolean wet : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                GameObject target = c.target(Master.RightPlayer, 8, 0, 5);
                GameObject adjacent = c.target(Master.RightPlayer, 8, 0, 5.7f);
                if (wet) target.getComponent(CommonEffectReceiver.class).onReceive(Effect.Wet);
                c.record(clips, "fire_shot", wet ? "wet" : "burn", wet ? "젖은 대상 · 상태 상쇄" : "명중 · 화상 지속 피해", wet ? "Wet target · status cancellation" : "Impact · burn damage", 5,
                        tick -> { if (tick == 12) new FireShotMagic().run(c.context, Master.LeftPlayer, new Vector3(8, 0, 5)); });
                assertThat(c.hp(target)).isLessThan(900);
                assertThat(c.hp(adjacent)).isLessThan(1000);
                assertThat(c.seenEffects).contains("Burn");
                if (wet) assertThat(target.getEffects()).doesNotContain(Effect.Wet, Effect.Burn);
            }
        }
        try (Capture c = new Capture()) {
            GameObject direct = c.target(Master.RightPlayer, 6, 0, 5);
            GameObject adjacent = c.target(Master.RightPlayer, 6.8f, 0, 5);
            GameObject distant = c.target(Master.RightPlayer, 10, 0, 5);
            c.record(clips, "lightning_explosion", "shock", "범위 피해 · 감전", "Area damage · shock", 4,
                    tick -> { if (tick == 12) new LightningExplosionMagic().run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
            assertThat(c.hp(direct)).isLessThan(1000);
            assertThat(c.hp(adjacent)).isLessThan(1000);
            assertThat(c.hp(distant)).isEqualTo(1000);
            assertThat(c.seenEffects).contains("Shock");
        }
        try (Capture c = new Capture()) {
            GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
            c.record(clips, "sand_storm", "area", "지속 영역 · 지속 피해", "Persistent area · ongoing damage", 5,
                    tick -> { if (tick == 12) new SandStormMagic().run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
            assertThat(c.hp(target)).isLessThan(980);
            assertThat(c.seenTypes).contains("SandStorm");
        }
        for (boolean rage : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                // Towerback stays in contact; unlike RockGolem it does not knock targets away.
                c.target(Master.RightPlayer, 6, 0, 5);
                new TowerbackMagic().run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
                GameObject subject = c.pending(PrefabType.Towerback);
                float baseline = subject.getComponent(IntervalAttacker.class).getAttackInterval().total();
                boolean[] active = {false}, restored = {false};
                c.record(clips, "frenzy_totem", rage ? "rage" : "baseline", rage ? "격분 · 진영 해제와 공격 가속 → 복귀" : "격분 전 · 기본 공격 속도", rage ? "Rage · neutral and faster → restored" : "Before rage · normal attacks", 7,
                        tick -> {
                            if (rage && tick == 12) new FrenzyMagic().run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
                            if (subject.getMaster() == Master.None) {
                                active[0] = true;
                                assertThat(subject.getComponent(IntervalAttacker.class).getAttackInterval().total()).isLessThan(baseline);
                            } else if (active[0]) restored[0] = true;
                        });
                if (rage) {
                    assertThat(active[0]).isTrue();
                    assertThat(restored[0]).isTrue();
                    assertThat(subject.getMaster()).isEqualTo(Master.LeftPlayer);
                    assertThat(subject.getComponent(IntervalAttacker.class).getAttackInterval().total()).isEqualTo(baseline);
                }
            }
        }
        for (boolean medium : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                GameObject remnant = new GameObject(Master.LeftPlayer, medium ? PrefabType.MediumRockRemnant : PrefabType.RockRemnant, new Vector3(6, 0, 5), c.context);
                GameObject target = c.target(Master.RightPlayer, 7.5f, 0, 5);
                c.record(clips, "earth_call", medium ? "medium" : "small", medium ? "중간 잔해 → 바위 골렘 · 공격" : "작은 잔해 → 미니 바위 · 공격", medium ? "Medium remnant → golem attacks" : "Small remnant → mini rock attacks", 5,
                        tick -> { if (tick == 20) new EarthCallMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
                assertThat(remnant.isDestroyed()).isTrue();
                assertThat(c.seenTypes).contains(medium ? "RockGolem" : "MiniRock");
                assertThat(c.hp(target)).isLessThan(1000);
            }
        }
        assertThat(clips).hasSize(9);
        return clips;
    }

    private static final class Capture implements AutoCloseable {
        final Parameters parameters;
        final GameContext context;
        final MockedStatic<PrefabProvider> provider;
        final Set<String> seenTypes = new HashSet<>(), seenEffects = new HashSet<>(), seenProjectiles = new HashSet<>();
        final Map<Integer, Integer> ids = new LinkedHashMap<>();
        final GameObjectAddRemoteSystem lifecycle = new GameObjectAddRemoteSystem();

        Capture() {
            // Fixed, named illustrative values. Unknown reads fail rather than Mockito's 0 default.
            Map<String, Double> common = Map.ofEntries(
                    Map.entry("hp", 1000d), Map.entry("mass", 10d), Map.entry("radius", 0.6d),
                    Map.entry("speed", 2d), Map.entry("damage", 100d), Map.entry("attack_interval", 0.8d),
                    Map.entry("attack_range", 3d), Map.entry("range", 3d), Map.entry("duration", 3d),
                    Map.entry("sub_speed", 1.5d), Map.entry("sub_damage", 80d), Map.entry("sub_attack_range", 0.5d),
                    Map.entry("buff_duration", 2d));
            Map<String, Double> status = Map.ofEntries(
                    Map.entry("burn_duration", 2d), Map.entry("burn_total_damage", 80d),
                    Map.entry("wet_duration", 8d), Map.entry("wet_nature_heal", 20d),
                    Map.entry("shock_stun_duration", 1d), Map.entry("shock_refresh_duration", 1d),
                    Map.entry("sandstorm_effect_duration", 1d), Map.entry("sandstorm_effect_damage", 30d));
            ParameterService service = mock(ParameterService.class);
            when(service.getValue(anyString(), anyString())).thenAnswer(call -> {
                String object = call.getArgument(0), key = call.getArgument(1);
                if (object.equals("fire_shot") && key.equals("speed")) return 8d;
                if ((object.equals("electric_explode") || object.equals("sand_storm") || object.equals("earth_call")) && key.equals("radius")) return 2d;
                if (object.equals("healing_totem") && key.equals("damage")) return -80d;
                if (object.equals("frenzy_totem") && key.equals("speed")) return 5d;
                Map<String, Double> values = object.equals("game") ? status : common;
                if (!values.containsKey(key)) throw new AssertionError("Missing preview fixture parameter " + object + "." + key);
                return values.get(key);
            });
            when(service.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(call -> call.getArgument(2));
            parameters = new Parameters(service);
            var data = new GameSessionData(new PlayerData(null), new PlayerData(null));
            context = new GameContext(null, data, parameters, null, null);
            context.setObjectsInfoDtoBuilder(new ObjectsInfoDtoBuilder(context));
            context.setPhysics(new SimplePhysics(data.gameObjects));
            context.setDeltaTime(DT);
            Map<PrefabType, PrefabInitializer> prefabs = new EnumMap<>(PrefabType.class);
            prefabs.put(PrefabType.RockGolem, new RockGolemPrefabInitializer(parameters));
            prefabs.put(PrefabType.WindSpirit, new WindSpiritPrefabInitializer(parameters));
            prefabs.put(PrefabType.Towerback, new TowerbackPrefabInitializer(parameters));
            prefabs.put(PrefabType.HealingTotem, new HealingTotemPrefabInitializer(parameters));
            prefabs.put(PrefabType.FireShot, new FireShotPrefabInitializer(parameters));
            prefabs.put(PrefabType.ElectricExplode, new ElectricExplodePrefabInitializer(parameters));
            prefabs.put(PrefabType.SandStorm, new SandStormPrefabInitializer(parameters));
            prefabs.put(PrefabType.FrenzyTotem, new FrenzyTotemPrefabInitializer(parameters));
            prefabs.put(PrefabType.EarthCall, new EarthCallPrefabInitializer());
            prefabs.put(PrefabType.RockRemnant, new RockRemnantPrefabInitializer(parameters));
            prefabs.put(PrefabType.MediumRockRemnant, new MediumRockRemnantPrefabInitializer(parameters));
            prefabs.put(PrefabType.MiniRock, new MiniRockPrefabInitializer(parameters));
            prefabs.put(PrefabType.Player, new PrefabInitializer(PrefabType.Player) {
                @Override public void initialize(GameObject object) { }
            });
            prefabs.put(PrefabType.ElectricSlime, new PrefabInitializer(PrefabType.ElectricSlime) {
                @Override public void initialize(GameObject object) {
                    object.addCollider(new CircleCollider(object, 0.35f, true));
                    object.addComponent(new PassiveTarget(object));
                    object.addComponent(new RigidBody(object, 10));
                    if (object.getPosition().getY() == 0) object.addComponent(new ZPhysics(object));
                    object.addComponent(new CommonEffectReceiver(object));
                }
            });
            provider = mockStatic(PrefabProvider.class, invocation -> {
                PrefabType type = invocation.getArgument(0);
                if (!prefabs.containsKey(type)) throw new AssertionError("Missing preview prefab " + type);
                return prefabs.get(type);
            });
            // Caster is visual-only; does not interfere with target detection/healing.
            new GameObject(Master.LeftPlayer, PrefabType.Player, new Vector3(2, 0, 5), context);
        }

        GameObject target(Master master, float x, float y, float z) {
            return new GameObject(master, PrefabType.ElectricSlime, new Vector3(x, y, z), context);
        }
        GameObject pending(PrefabType type) {
            return context.getGameSessionData().gameObjectsToAdd.stream().filter(o -> o.getType() == type).findFirst().orElseThrow();
        }
        int hp(GameObject object) { return object.getComponent(Mob.class).getHp(); }
        int id(int original) { return ids.computeIfAbsent(original, ignored -> ids.size() + 1); }

        void record(Map<String, ObjectNode> clips, String magic, String scenarioId, String ko, String en, float duration, IntConsumer action) {
            ObjectNode clip = clips.computeIfAbsent(magic, name -> {
                ObjectNode node = JSON.createObjectNode();
                node.put("version", 2).put("magic", name).put("frameDuration", DT);
                node.put("source", "MagicScenarioPreviewTest; production mechanics; fixed illustrative parameters; passive fixture targets");
                node.putArray("scenarios");
                return node;
            });
            ObjectNode scenario = clip.withArray("scenarios").addObject();
            scenario.put("id", scenarioId).put("labelKo", ko).put("labelEn", en).put("duration", duration);
            var frames = scenario.putArray("frames");
            lifecycle.update(context);
            var reset = new GameObjectStateInitialSystem();
            var physics = new PhysicSystem();
            for (int tick = 0; tick < Math.round(duration / DT); tick++) {
                reset.update(context);
                action.accept(tick);
                for (GameObject object : List.copyOf(context.getActiveGameObjects())) object.update();
                physics.update(context);
                // Real snapshots provide initial HP/effects too; create DTOs do not carry gauges.
                for (GameObject object : context.getActiveGameObjects()) object.applyUpdate();
                ObjectNode frame = frames.addObject();
                frame.put("time", tick * (double) DT);
                JsonNode objects = JSON.valueToTree(context.getObjectsInfoDto());
                for (String key : new String[]{"create", "update"}) {
                    for (JsonNode entry : objects.path(key)) {
                        ((ObjectNode) entry).put("id", id(entry.path("id").asInt()));
                        if (key.equals("create")) seenTypes.add(entry.path("type").asText());
                        for (JsonNode effect : entry.path("effects")) seenEffects.add(effect.asText());
                    }
                }
                for (JsonNode projectile : objects.path("projectile")) {
                    seenProjectiles.add(projectile.path("type").asText());
                    for (String endpoint : new String[]{"start", "end"}) {
                        JsonNode point = projectile.path(endpoint);
                        if (point.path("targetType").asText().equals("reference"))
                            ((ObjectNode) point).put("id", id(point.path("id").asInt()));
                    }
                }
                // Collision pairs use a HashSet; independent updates have no wire ordering contract.
                for (String key : new String[]{"create", "update"}) {
                    var array = (com.fasterxml.jackson.databind.node.ArrayNode) objects.path(key);
                    List<JsonNode> sorted = new ArrayList<>();
                    array.forEach(sorted::add);
                    sorted.sort(Comparator.comparingInt(node -> node.path("id").asInt()));
                    array.removeAll().addAll(sorted);
                }
                frame.set("objects", objects);
                var events = frame.putArray("events");
                for (var event : context.drainEvents()) {
                    ObjectNode node = JSON.valueToTree(event);
                    node.put("actorId", id(event.actorId()));
                    node.put("targetId", id(event.targetId()));
                    events.add(node);
                }
                List<JsonNode> sortedEvents = new ArrayList<>();
                events.forEach(sortedEvents::add);
                sortedEvents.sort(Comparator.comparing(JsonNode::toString));
                events.removeAll().addAll(sortedEvents);
                lifecycle.update(context);
            }
        }
        @Override public void close() { provider.close(); }
    }

    private static final class PassiveTarget extends Mob implements Collidable {
        PassiveTarget(GameObject object) { super(object, 1000, 0); }
        @Override public void start() { }
        @Override public void onDestroy() { }
        @Override public void onDeath() { gameObject.destroy(); }
        @Override public void onCollisionWithEnemy(GameObject other) { }
    }
}
