package com.wordonline.server.game.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.implement.build.*;
import com.wordonline.server.game.domain.magic.implement.drop.*;
import com.wordonline.server.game.domain.magic.implement.explode.*;
import com.wordonline.server.game.domain.magic.implement.shoot.*;
import com.wordonline.server.game.domain.magic.implement.spawn.*;
import com.wordonline.server.game.domain.object.*;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.*;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.domain.object.prefab.implement.drop.FrenzyTotemPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.fire.FireShotPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.*;
import com.wordonline.server.game.domain.object.prefab.implement.rock.*;
import com.wordonline.server.game.domain.object.prefab.implement.wind.WindSpiritPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.wind.WindBladePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.water.*;
import com.wordonline.server.game.domain.object.prefab.implement.lightning.ElectricShotPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.third.ThunderBirdPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.drop.FireDropPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.drop.WindDropPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.lightning.ElectricFieldPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.explode.*;
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
        captureExpansion(clips);
        assertThat(clips).hasSize(21);
        return clips;
    }

    private void captureExpansion(Map<String, ObjectNode> clips) {
        for (boolean lightning : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                GameObject target = c.target(Master.RightPlayer, 7, 0, 5);
                GameObject adjacent = c.target(Master.RightPlayer, 7, 0, 5.7f);
                Magic magic = lightning ? new LightningShotMagic() : new WaterShotMagic();
                c.record(clips, lightning ? "lightning_shot" : "water_shot", "impact",
                        lightning ? "직격·주변 피해와 감전" : "직격·주변 피해와 젖음",
                        lightning ? "Impact, splash and Shock" : "Impact, splash and Wet", 5,
                        tick -> { if (tick == 12) magic.run(c.context, Master.LeftPlayer, new Vector3(7, 0, 5)); });
                assertThat(c.hp(target)).isLessThan(1000);
                assertThat(c.hp(adjacent)).isLessThan(1000);
                assertThat(c.seenEffects).contains(lightning ? "Shock" : "Wet");
            }
        }
        try (Capture c = new Capture()) {
            new thunderBirdSwarmMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
            c.stageSwarm(PrefabType.ThunderBird, 5);
            GameObject ally = c.pending(PrefabType.ThunderBird);
            c.record(clips, "lightning_shot", "overcharge", "아군 전기 소환수 과충전·추가 번개",
                    "Allied lightning summon overcharge and extra shots", 7,
                    tick -> {
                        if (tick == 12) new LightningShotMagic().run(c.context, Master.LeftPlayer, new Vector3(ally.getPosition()));
                        if (tick == 45) c.target(Master.RightPlayer, 6.5f, 0, 5);
                    });
            assertThat(c.seenEffects).contains("Overcharge");
            assertThat(c.seenProjectiles).contains("ElectricShot");
        }
        try (Capture c = new Capture()) {
            GameObject first = c.target(Master.RightPlayer, 5, 0, 5);
            GameObject second = c.target(Master.RightPlayer, 7, 0, 5);
            GameObject third = c.target(Master.RightPlayer, 9, 0, 5);
            c.record(clips, "wind_blade", "pierce", "연속 관통·피해 감소", "Piercing with damage decay", 4,
                    tick -> { if (tick == 12) new WindBladeMagic().run(c.context, Master.LeftPlayer, new Vector3(9, 0, 5)); });
            assertThat(c.hp(first)).isLessThan(c.hp(second));
            assertThat(c.hp(second)).isLessThan(c.hp(third));
            assertThat(c.hp(third)).isLessThan(1000);
        }
        try (Capture c = new Capture()) {
            GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
            boolean[] reflected = {false};
            c.record(clips, "rock_rolling", "bounce", "적과 충돌 후 반사", "Bounce off an enemy", 4,
                    tick -> {
                        if (tick == 12) new RockRollingMagic().run(c.context, Master.LeftPlayer, new Vector3(6.5f, 0, 5));
                        for (GameObject object : c.context.getActiveGameObjects()) {
                            if (object.getType() == PrefabType.RockRolling &&
                                    object.getComponent(com.wordonline.server.game.domain.object.component.magic.RollingRock.class).getDirection().getX() < 0)
                                reflected[0] = true;
                        }
                    });
            assertThat(c.hp(target)).isLessThan(1000);
            assertThat(reflected[0]).isTrue();
        }
        for (boolean fire : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                GameObject target = c.target(Master.RightPlayer, 6, 0, 5);
                Magic magic = fire ? new FireDropMagic() : new WindDropMagic();
                c.record(clips, fire ? "fire_drop" : "wind_drop", "fall", "낙하·직격 피해", "Drop and direct impact", 4,
                        tick -> { if (tick == 12) magic.run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
                assertThat(c.hp(target)).isLessThan(1000);
                assertThat(c.seenTypes).contains(fire ? "FireDrop" : "WindDrop");
                // Production Drop has no status provider; do not invent Burn/Knockback.
                assertThat(c.seenEffects).doesNotContain("Burn", "Knockback");
            }
        }
        for (boolean water : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
                GameObject adjacent = c.target(Master.RightPlayer, 7, 0, 5);
                GameObject distant = c.target(Master.RightPlayer, 10, 0, 5);
                float[] peakHeight = {0};
                Magic magic = water ? new WaterExplosionMagic() : new WindExplosionMagic();
                c.record(clips, water ? "water_explosion" : "wind_explosion", "area",
                        water ? "범위 피해·화상·공중 띄우기" : "범위 피해·밀치기",
                        water ? "Area damage, Burn and launch" : "Area damage and knockback", 4,
                        tick -> {
                            if (tick == 12) magic.run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5));
                            peakHeight[0] = Math.max(peakHeight[0], target.getPosition().getY());
                        });
                assertThat(c.hp(target)).isLessThan(1000);
                assertThat(c.hp(adjacent)).isLessThan(1000);
                assertThat(c.hp(distant)).isEqualTo(1000);
                assertThat(c.seenEffects).contains(water ? "Burn" : "Knockback");
                if (water) assertThat(peakHeight[0]).isGreaterThan(0.2f);
                else assertThat(target.getPosition().getX()).isGreaterThan(6.5f);
            }
        }
        for (boolean medium : new boolean[]{false, true}) {
            try (Capture c = new Capture()) {
                GameObject remnant = new GameObject(Master.LeftPlayer, medium ? PrefabType.MediumRockRemnant : PrefabType.RockRemnant, new Vector3(6, 0, 5), c.context);
                GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
                c.record(clips, "rock_blast", medium ? "medium" : "small",
                        medium ? "중간 잔해 폭발·작은 잔해 잔존" : "작은 잔해 폭발·소모",
                        medium ? "Medium remnant bursts, small remnant remains" : "Small remnant bursts and is consumed", 4,
                        tick -> { if (tick == 12) new RockBlastMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(6, 0, 5)); });
                assertThat(remnant.isDestroyed()).isTrue();
                assertThat(c.seenTypes).contains("RockExplode");
                assertThat(c.hp(target)).isLessThan(1000);
                assertThat(c.context.getActiveGameObjects().stream().anyMatch(o -> o.getType() == PrefabType.RockRemnant)).isEqualTo(medium);
            }
        }
        for (PrefabType type : new PrefabType[]{PrefabType.MiniRock, PrefabType.ThunderBird, PrefabType.WaterSlime}) {
            try (Capture c = new Capture()) {
                GameObject target = c.target(Master.RightPlayer, type == PrefabType.WaterSlime ? 9 : 6.5f, 0, 5);
                Magic magic = switch (type) {
                    case MiniRock -> new MiniRockSwarmMagic(c.parameters);
                    case ThunderBird -> new thunderBirdSwarmMagic(c.parameters);
                    default -> new WaterSlimeSwarmMagic(c.parameters);
                };
                String name = switch (type) {
                    case MiniRock -> "mini_rock_swarm";
                    case ThunderBird -> "thunder_bird_swarm";
                    default -> "water_slime_swarm";
                };
                c.record(clips, name, "ground", "무리 소환·지상 공격", "Swarm summon and ground attack", 6,
                        tick -> {
                            if (tick == 12) {
                                magic.run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5));
                                c.stageSwarm(type, 4);
                            }
                        });
                assertThat(c.hp(target)).isLessThan(1000);
                if (type == PrefabType.WaterSlime) {
                    assertThat(c.seenProjectiles).contains("WaterShot");
                    assertThat(c.seenTypes).contains("WaterField");
                }
            }
        }
        try (Capture c = new Capture()) {
            GameObject target = c.target(Master.RightPlayer, 9, 0, 5);
            c.record(clips, "water_slime_swarm", "trail", "이동 경로 물 필드·젖음", "Water trail and Wet", 6,
                    tick -> {
                        if (tick == 12) {
                            new WaterSlimeSwarmMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(4, 0, 5));
                            c.stageSwarm(PrefabType.WaterSlime, 4);
                        }
                        // Place a passive victim onto a real emitted trail; movement/field logic is real.
                        if (tick == 55) c.context.getActiveGameObjects().stream()
                                .filter(o -> o.getType() == PrefabType.WaterField).findFirst()
                                .ifPresent(field -> target.setPosition(new Vector3(field.getPosition())));
                    });
            assertThat(c.seenTypes).contains("WaterField");
            assertThat(c.seenEffects).contains("Wet");
        }
        try (Capture c = new Capture()) {
            new thunderBirdSwarmMagic(c.parameters).run(c.context, Master.LeftPlayer, new Vector3(5, 0, 5));
            c.stageSwarm(PrefabType.ThunderBird, 5);
            GameObject dying = c.pending(PrefabType.ThunderBird);
            c.record(clips, "thunder_bird_swarm", "death_energy", "사망 낙하·전기 필드·아군 에너지 흡수",
                    "Death fall, electric field and ally absorption", 7,
                    tick -> { if (tick == 20) dying.getComponent(Mob.class).onDamaged(new AttackInfo(10000, com.wordonline.server.game.domain.magic.ElementType.NONE)); });
            assertThat(dying.isDestroyed()).isTrue();
            assertThat(c.seenTypes).contains("ElectricField");
            assertThat(c.seenProjectiles).contains("ElectricAbsorb");
            assertThat(c.seenEffects).contains("Overcharge");
        }
    }

    static final class Capture implements AutoCloseable {
        final Parameters parameters;
        final GameContext context;
        final MockedStatic<PrefabProvider> provider;
        final Set<String> seenTypes = new HashSet<>(), seenEffects = new HashSet<>(), seenProjectiles = new HashSet<>();
        final Map<Integer, Integer> ids = new LinkedHashMap<>();
        final List<GameObject> fixtureTargets = new ArrayList<>();
        final Map<String, Double> fixtureParameters = new TreeMap<>();
        final GameObjectAddRemoteSystem lifecycle = new GameObjectAddRemoteSystem();
        final boolean expanded;
        final MockedStatic<java.util.concurrent.ThreadLocalRandom> randomSource;
        final MockedStatic<Vector3> directionSource;
        ObjectNode lastScenario;
        boolean creatingTarget;

        Capture() {
            this(false);
        }

        Capture(boolean expanded) {
            this.expanded = expanded;
            if (expanded) {
                // Control draws, not trajectories: production random-spawn algorithms still run.
                var random = mock(java.util.concurrent.ThreadLocalRandom.class);
                var seeded = new Random(20468);
                when(random.nextDouble()).thenAnswer(a -> seeded.nextDouble());
                when(random.nextFloat()).thenAnswer(a -> seeded.nextFloat());
                when(random.nextDouble(anyDouble(), anyDouble())).thenAnswer(a -> {
                    double low = a.getArgument(0), high = a.getArgument(1);
                    return low + seeded.nextDouble() * (high - low);
                });
                when(random.nextFloat(anyFloat(), anyFloat())).thenAnswer(a -> {
                    float low = a.getArgument(0), high = a.getArgument(1);
                    return low + seeded.nextFloat() * (high - low);
                });
                when(random.nextInt(anyInt())).thenAnswer(a -> seeded.nextInt((int) a.getArgument(0)));
                when(random.nextInt(anyInt(), anyInt())).thenAnswer(a -> seeded.nextInt((int) a.getArgument(0), (int) a.getArgument(1)));
                randomSource = mockStatic(java.util.concurrent.ThreadLocalRandom.class, CALLS_REAL_METHODS);
                randomSource.when(java.util.concurrent.ThreadLocalRandom::current).thenReturn(random);
                directionSource = mockStatic(Vector3.class, CALLS_REAL_METHODS);
                directionSource.when(Vector3::randomUnitVector).thenAnswer(a -> {
                    double angle = seeded.nextDouble() * Math.PI * 2;
                    return new Vector3((float) Math.cos(angle), 0, (float) Math.sin(angle));
                });
            } else { randomSource = null; directionSource = null; }
            // Fixed, named illustrative values. Unknown reads fail rather than Mockito's 0 default.
            Map<String, Double> common = Map.ofEntries(
                    Map.entry("hp", 1000d), Map.entry("mass", 10d), Map.entry("radius", 0.6d),
                    Map.entry("speed", 2d), Map.entry("damage", 100d), Map.entry("attack_interval", 0.8d),
                    Map.entry("attack_range", 3d), Map.entry("range", 3d), Map.entry("duration", 3d),
                    Map.entry("sub_speed", 1.5d), Map.entry("sub_damage", 80d), Map.entry("sub_attack_range", 0.5d),
                    Map.entry("buff_duration", 2d), Map.entry("z_force", 5d));
            Map<String, Double> status = Map.ofEntries(
                    Map.entry("burn_duration", 2d), Map.entry("burn_total_damage", 80d),
                    Map.entry("wet_duration", 8d), Map.entry("wet_nature_heal", 20d),
                    Map.entry("shock_stun_duration", 1d), Map.entry("shock_refresh_duration", 1d),
                    Map.entry("sandstorm_effect_duration", 1d), Map.entry("sandstorm_effect_damage", 30d));
            ParameterService service = mock(ParameterService.class);
            org.mockito.stubbing.Answer<Double> fixtureValue = call -> {
                String object = call.getArgument(0), key = call.getArgument(1);
                if (expanded) return RemainingMagicPreviewTest.fixtureValue(object, key);
                if (Set.of("fire_shot", "water_shot", "electric_shot", "wind_blade", "rock_rolling").contains(object) && key.equals("speed")) return 8d;
                // Avoid simultaneous first-contact ties; splash still reaches the adjacent fixture.
                if (Set.of("water_shot", "electric_shot").contains(object) && key.equals("radius")) return 0.3d;
                if (Set.of("fire_drop", "wind_drop", "wind_explode", "water_explosion", "rock_explode", "rock_blast").contains(object) && key.equals("radius")) return 2d;
                if (object.equals("electric_field") && key.equals("radius")) return 5d;
                if ((object.equals("electric_explode") || object.equals("sand_storm") || object.equals("earth_call")) && key.equals("radius")) return 2d;
                if (object.equals("healing_totem") && key.equals("damage")) return -80d;
                if (object.equals("frenzy_totem") && key.equals("speed")) return 5d;
                Map<String, Double> values = object.equals("game") ? status : common;
                if (!values.containsKey(key)) throw new AssertionError("Missing preview fixture parameter " + object + "." + key);
                return values.get(key);
            };
            when(service.getValue(anyString(), anyString())).thenAnswer(call -> {
                double value = fixtureValue.answer(call);
                fixtureParameters.put(call.getArgument(0) + "." + call.getArgument(1), value);
                return value;
            });
            when(service.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(call -> {
                double value = fixtureDefault(call.getArgument(0), call.getArgument(1), call.getArgument(2), expanded);
                fixtureParameters.put(call.getArgument(0) + "." + call.getArgument(1), value);
                return value;
            });
            /* Defaults are recorded too: runtime aura scaling must not consult live DB cache. */
            parameters = new Parameters(service);
            var data = new GameSessionData(new PlayerData(expanded ? new ManaCharger(parameters) : null), new PlayerData(expanded ? new ManaCharger(parameters) : null));
            if (expanded) data.leftPlayerData.manaCharger.initMaxMana();
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
            prefabs.put(PrefabType.WaterShot, new WaterShotPrefabInitializer(parameters));
            prefabs.put(PrefabType.ElectricShot, new ElectricShotPrefabInitializer(parameters));
            prefabs.put(PrefabType.WindBlade, new WindBladePrefabInitializer(parameters));
            prefabs.put(PrefabType.RockRolling, new RockRollingPrefabInitializer(parameters));
            prefabs.put(PrefabType.FireDrop, new FireDropPrefabInitializer(parameters));
            prefabs.put(PrefabType.WindDrop, new WindDropPrefabInitializer(parameters));
            prefabs.put(PrefabType.WaterExplosion, new WaterExplosionPrefabInitializer(parameters));
            prefabs.put(PrefabType.WindExplode, new WindExplodePrefabInitializer(parameters));
            prefabs.put(PrefabType.RockExplode, new RockExplodePrefabInitializer(parameters));
            prefabs.put(PrefabType.WaterSlime, new WaterSlimePrefabInitializer(parameters));
            prefabs.put(PrefabType.ThunderBird, new ThunderBirdPrefabInitializer(parameters));
            prefabs.put(PrefabType.WaterField, new WaterFieldPrefabInitializer(parameters));
            prefabs.put(PrefabType.ElectricField, new ElectricFieldPrefabInitializer(parameters));
            prefabs.put(PrefabType.Player, new PrefabInitializer(PrefabType.Player) {
                @Override public void initialize(GameObject object) { }
            });
            prefabs.put(PrefabType.ElectricSlime, new PrefabInitializer(PrefabType.ElectricSlime) {
                @Override public void initialize(GameObject object) {
                    if (expanded && !creatingTarget) {
                        RemainingMagicPreviewTest.initializer(PrefabType.ElectricSlime, parameters).initialize(object);
                        return;
                    }
                    object.addCollider(new CircleCollider(object, 0.35f, true));
                    object.addComponent(new PassiveTarget(object, expanded ? 10000 : 1000));
                    object.addComponent(new RigidBody(object, 10));
                    if (object.getPosition().getY() == 0) object.addComponent(new ZPhysics(object));
                    object.addComponent(new CommonEffectReceiver(object));
                }
            });
            provider = mockStatic(PrefabProvider.class, invocation -> {
                PrefabType type = invocation.getArgument(0);
                if (!prefabs.containsKey(type) && expanded)
                    prefabs.put(type, RemainingMagicPreviewTest.initializer(type, parameters));
                if (!prefabs.containsKey(type)) throw new AssertionError("Missing preview prefab " + type);
                return prefabs.get(type);
            });
            // Caster is visual-only; does not interfere with target detection/healing.
            new GameObject(Master.LeftPlayer, PrefabType.Player, new Vector3(2, 0, 5), context);
        }

        GameObject target(Master master, float x, float y, float z) {
            creatingTarget = true;
            try {
                GameObject object = new GameObject(master, PrefabType.ElectricSlime, new Vector3(x, y, z), context);
                fixtureTargets.add(object);
                return object;
            }
            finally { creatingTarget = false; }
        }
        private static double fixtureDefault(String object, String key, double fallback, boolean expanded) {
            if (key.equals("quantity") && (expanded || Set.of("mini_rock", "thunder_bird", "water_slime").contains(object))) return 3;
            return fallback;
        }
        GameObject pending(PrefabType type) {
            return context.getGameSessionData().gameObjectsToAdd.stream().filter(o -> o.getType() == type).findFirst().orElseThrow();
        }
        int hp(GameObject object) { return object.getComponent(Mob.class).getHp(); }
        void stageSwarm(PrefabType type, float x) {
            List<GameObject> swarm = context.getGameSessionData().gameObjectsToAdd.stream()
                    .filter(o -> o.getType() == type).toList();
            assertThat(swarm).hasSize(3);
            for (int i = 0; i < swarm.size(); i++) {
                GameObject object = swarm.get(i);
                // The queued create DTO retains this Vector3 reference: stage it too, before capture.
                object.getPosition().setX(x);
                object.getPosition().setZ(4.4f + i * 0.6f);
            }
        }
        int id(int original) { return ids.computeIfAbsent(original, ignored -> ids.size() + 1); }

        void record(Map<String, ObjectNode> clips, String magic, String scenarioId, String ko, String en, float duration, IntConsumer action) {
            ObjectNode clip = clips.computeIfAbsent(magic, name -> {
                ObjectNode node = JSON.createObjectNode();
                node.put("version", 2).put("magic", name).put("frameDuration", DT);
                node.put("source", expanded ? "RemainingMagicPreviewTest; production mechanics; fixed illustrative parameters; passive fixture targets; seeded random draws and collision directions" : "MagicScenarioPreviewTest; production mechanics; fixed illustrative parameters; passive fixture targets; swarms staged deterministically within spawn range");
                node.putArray("scenarios");
                return node;
            });
            ObjectNode scenario = clip.withArray("scenarios").addObject();
            lastScenario = scenario;
            scenario.put("id", scenarioId).put("labelKo", ko).put("labelEn", en).put("duration", duration);
            var frames = scenario.putArray("frames");
            lifecycle.update(context);
            var reset = new GameObjectStateInitialSystem();
            var physics = new PhysicSystem();
            if (expanded) {
                // Pair iteration has no production ordering contract. Fix only fixture order
                // so multi-body overlaps do not depend on JVM identity hash codes.
                try {
                    var pairs = PhysicSystem.class.getDeclaredField("collidedPairs");
                    pairs.setAccessible(true);
                    pairs.set(physics, new LinkedHashSet<>());
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
            for (int tick = 0; tick < Math.round(duration / DT); tick++) {
                reset.update(context);
                action.accept(tick);
                for (GameObject object : List.copyOf(context.getActiveGameObjects())) object.update();
                if (expanded) {
                    // Scope construction mocks to one tick, not an entire multi-clip catalog.
                    try (var pairs = mockConstruction(com.wordonline.server.game.util.Pair.class, withSettings().stubOnly(), (pair, construction) -> {
                        when(pair.a()).thenReturn(construction.arguments().get(0));
                        when(pair.b()).thenReturn(construction.arguments().get(1));
                    })) { physics.update(context); }
                } else physics.update(context);
                // Real snapshots provide initial HP/effects too; create DTOs do not carry gauges.
                for (GameObject object : context.getActiveGameObjects()) object.applyUpdate();
                ObjectNode frame = frames.addObject();
                frame.put("time", tick * (double) DT);
                if (expanded) {
                    // ManaWell's real charger is not a GameObject gauge; record its actual rate.
                    try {
                        var manaField = ManaCharger.class.getDeclaredField("manaChangeValue");
                        manaField.setAccessible(true);
                        frame.put("manaRate", ((com.wordonline.server.game.domain.Stat) manaField.get(context.getGameSessionData().leftPlayerData.manaCharger)).total());
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                }
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
            var targetIds = scenario.putArray("fixtureTargetIds");
            fixtureTargets.stream().map(object -> ids.get(object.getId())).filter(Objects::nonNull).sorted().forEach(targetIds::add);
            scenario.set("parameters", JSON.valueToTree(fixtureParameters));
        }
        @Override public void close() {
            provider.close();
            if (directionSource != null) directionSource.close();
            if (randomSource != null) randomSource.close();
            if (expanded) org.mockito.Mockito.framework().clearInlineMocks();
        }
    }

    private static final class PassiveTarget extends Mob implements Collidable {
        PassiveTarget(GameObject object, int hp) { super(object, hp, 0); }
        @Override public void start() { }
        @Override public void onDestroy() { }
        @Override public void onDeath() { gameObject.destroy(); }
        @Override public void onCollisionWithEnemy(GameObject other) { }
    }
}
