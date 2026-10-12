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

/** Ordered representative situations; all combat uses current server inputs. */
final class RepresentativePreviewScenarios {

    private final String selectedName;
    private final Map<String, Double> inputs;
    private final Map<String, Class<? extends PrefabInitializer>> prefabs;
    private final Map<String, Magic> magics;
    private final PreviewProperties limits;

    RepresentativePreviewScenarios(String selectedName, Map<String, Double> inputs,
          Map<String, Class<? extends PrefabInitializer>> prefabs,
          Map<String, Magic> magics, PreviewProperties limits) {
        this.selectedName = selectedName;
        this.inputs = inputs;
        this.prefabs = prefabs;
        this.magics = magics;
        this.limits = limits;
    }

    private PreviewCapture capture() { return new PreviewCapture(inputs, prefabs, magics, limits); }

    Map<String, ObjectNode> captureAll() {
        Map<String, ObjectNode> clips = new LinkedHashMap<>();
        if (selectedName.equals("rock_golem")) try (PreviewCapture c = capture()) {
            GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
            c.record(clips, "rock_golem", "ground", "지상 공격 · 밀치기", "Ground attack · knockback", 4,
                    tick -> { if (tick == 12) c.cast(new RockGolemMagic(c.parameters), new Vector3(5, 0, 5)); });
        }
        if (selectedName.equals("rock_golem")) try (PreviewCapture c = capture()) {
            c.cast(new RockGolemMagic(c.parameters), new Vector3(5, 0, 5));
            GameObject golem = c.pending(PrefabType.RockGolem);
            c.record(clips, "rock_golem", "death", "사망 후 바위 잔해", "Rock remnant on death", 3,
                    tick -> { if (tick == 20) golem.getComponent(Mob.class).onDamaged(new AttackInfo(10000, com.wordonline.server.game.domain.magic.ElementType.NONE)); });
        }
        if (selectedName.equals("wind_spirit")) try (PreviewCapture c = capture()) {
            GameObject target = c.target(Master.RightPlayer, 7, 2, 5);
            GameObject adjacent = c.target(Master.RightPlayer, 7.9f, 2, 5.8f);
            c.record(clips, "wind_spirit", "air", "공중 돌진 · 자폭", "Air charge · self-destruct", 4,
                    tick -> { if (tick == 12) c.cast(new WindSpiritMagic(c.parameters), new Vector3(4, 0, 5)); });
        }
        for (boolean air : new boolean[]{false, true}) {
            if (selectedName.equals("towerback")) try (PreviewCapture c = capture()) {
                GameObject target = c.target(Master.RightPlayer, air ? 7 : 6, air ? 2 : 0, 5);
                GameObject adjacent = air ? c.target(Master.RightPlayer, 7.9f, 2, 5.8f) : null;
                c.record(clips, "towerback", air ? "air" : "ground", air ? "공중 공격 · 포탄 범위 피해" : "지상 공격", air ? "Air attack · splash" : "Ground attack", 4,
                        tick -> { if (tick == 12) c.cast(new TowerbackMagic(), new Vector3(4.5f, 0, 5)); });
            }
        }
        if (selectedName.equals("healing_totem")) try (PreviewCapture c = capture()) {
            GameObject ally = c.target(Master.LeftPlayer, 6, 0, 5);
            ally.getComponent(Mob.class).onDamaged(new AttackInfo(500, com.wordonline.server.game.domain.magic.ElementType.NONE));
            c.record(clips, "healing_totem", "heal", "부상당한 아군 회복", "Heal an injured ally", 4,
                    tick -> { if (tick == 12) c.cast(new HealingTotemMagic(), new Vector3(5, 0, 5)); });
        }
        for (boolean wet : new boolean[]{false, true}) {
            if (selectedName.equals("fire_shot")) try (PreviewCapture c = capture()) {
                GameObject target = c.target(Master.RightPlayer, 8, 0, 5);
                GameObject adjacent = c.target(Master.RightPlayer, 8, 0, 5.7f);
                if (wet) target.getComponent(CommonEffectReceiver.class).onReceive(Effect.Wet);
                c.record(clips, "fire_shot", wet ? "wet" : "burn", wet ? "젖은 대상 · 상태 상쇄" : "명중 · 화상 지속 피해", wet ? "Wet target · status cancellation" : "Impact · burn damage", 5,
                        tick -> { if (tick == 12) c.cast(new FireShotMagic(), new Vector3(8, 0, 5)); });
            }
        }
        if (selectedName.equals("lightning_explosion")) try (PreviewCapture c = capture()) {
            GameObject direct = c.target(Master.RightPlayer, 6, 0, 5);
            GameObject adjacent = c.target(Master.RightPlayer, 6.8f, 0, 5);
            GameObject distant = c.target(Master.RightPlayer, 10, 0, 5);
            c.record(clips, "lightning_explosion", "shock", "범위 피해 · 감전", "Area damage · shock", 4,
                    tick -> { if (tick == 12) c.cast(new LightningExplosionMagic(), new Vector3(6, 0, 5)); });
        }
        if (selectedName.equals("sand_storm")) try (PreviewCapture c = capture()) {
            GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
            c.record(clips, "sand_storm", "area", "지속 영역 · 지속 피해", "Persistent area · ongoing damage", 5,
                    tick -> { if (tick == 12) c.cast(new SandStormMagic(), new Vector3(6, 0, 5)); });
        }
        for (boolean rage : new boolean[]{false, true}) {
            if (selectedName.equals("frenzy_totem")) try (PreviewCapture c = capture()) {
                // Towerback stays in contact; unlike RockGolem it does not knock targets away.
                c.target(Master.RightPlayer, 6, 0, 5);
                c.cast(new TowerbackMagic(), new Vector3(5, 0, 5));
                c.record(clips, "frenzy_totem", rage ? "rage" : "baseline", rage ? "격분 · 진영 해제와 공격 가속 → 복귀" : "격분 전 · 기본 공격 속도", rage ? "Rage · neutral and faster → restored" : "Before rage · normal attacks", 7,
                        tick -> {
                            if (rage && tick == 12) c.cast(new FrenzyMagic(), new Vector3(5, 0, 5));
                        });
            }
        }
        for (boolean medium : new boolean[]{false, true}) {
            if (selectedName.equals("earth_call")) try (PreviewCapture c = capture()) {
                GameObject remnant = new GameObject(Master.LeftPlayer, medium ? PrefabType.MediumRockRemnant : PrefabType.RockRemnant, new Vector3(6, 0, 5), c.context);
                GameObject target = c.target(Master.RightPlayer, 7.5f, 0, 5);
                c.record(clips, "earth_call", medium ? "medium" : "small", medium ? "중간 잔해 → 바위 골렘 · 공격" : "작은 잔해 → 미니 바위 · 공격", medium ? "Medium remnant → golem attacks" : "Small remnant → mini rock attacks", 5,
                        tick -> { if (tick == 20) c.cast(new EarthCallMagic(c.parameters), new Vector3(6, 0, 5)); });
            }
        }
        captureExpansion(clips);

        return clips;
    }

private void captureExpansion(Map<String, ObjectNode> clips) {
        for (boolean lightning : new boolean[]{false, true}) {
            if (selectedName.equals(lightning ? "lightning_shot" : "water_shot")) try (PreviewCapture c = capture()) {
                GameObject target = c.target(Master.RightPlayer, 7, 0, 5);
                GameObject adjacent = c.target(Master.RightPlayer, 7, 0, 5.7f);
                Magic magic = lightning ? new LightningShotMagic() : new WaterShotMagic();
                c.record(clips, lightning ? "lightning_shot" : "water_shot", "impact",
                        lightning ? "직격·주변 피해와 감전" : "직격·주변 피해와 젖음",
                        lightning ? "Impact, splash and Shock" : "Impact, splash and Wet", 5,
                        tick -> { if (tick == 12) c.cast(magic, new Vector3(7, 0, 5)); });
            }
        }
        if (selectedName.equals("lightning_shot")) try (PreviewCapture c = capture()) {
            c.cast(new thunderBirdSwarmMagic(c.parameters), new Vector3(5, 0, 5));
            c.stageSwarm(PrefabType.ThunderBird, 5);
            GameObject ally = c.pending(PrefabType.ThunderBird);
            c.record(clips, "lightning_shot", "overcharge", "아군 전기 소환수 과충전·추가 번개",
                    "Allied lightning summon overcharge and extra shots", 7,
                    tick -> {
                        if (tick == 12) c.cast(new LightningShotMagic(), new Vector3(ally.getPosition()));
                        if (tick == 45) c.target(Master.RightPlayer, 6.5f, 0, 5);
                    });
        }
        if (selectedName.equals("wind_blade")) try (PreviewCapture c = capture()) {
            GameObject first = c.target(Master.RightPlayer, 5, 0, 5);
            GameObject second = c.target(Master.RightPlayer, 7, 0, 5);
            GameObject third = c.target(Master.RightPlayer, 9, 0, 5);
            c.record(clips, "wind_blade", "pierce", "연속 관통·피해 감소", "Piercing with damage decay", 4,
                    tick -> { if (tick == 12) c.cast(new WindBladeMagic(), new Vector3(9, 0, 5)); });
        }
        if (selectedName.equals("rock_rolling")) try (PreviewCapture c = capture()) {
            GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
            c.record(clips, "rock_rolling", "bounce", "적과 충돌 후 반사", "Bounce off an enemy", 4,
                    tick -> {
                        if (tick == 12) c.cast(new RockRollingMagic(), new Vector3(6.5f, 0, 5));
                    });
        }
        for (boolean fire : new boolean[]{false, true}) {
            if (selectedName.equals(fire ? "fire_drop" : "wind_drop")) try (PreviewCapture c = capture()) {
                GameObject target = c.target(Master.RightPlayer, 6, 0, 5);
                Magic magic = fire ? new FireDropMagic() : new WindDropMagic();
                c.record(clips, fire ? "fire_drop" : "wind_drop", "fall", "낙하·직격 피해", "Drop and direct impact", 4,
                        tick -> { if (tick == 12) c.cast(magic, new Vector3(6, 0, 5)); });
            }
        }
        for (boolean water : new boolean[]{false, true}) {
            if (selectedName.equals(water ? "water_explosion" : "wind_explosion")) try (PreviewCapture c = capture()) {
                GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
                GameObject adjacent = c.target(Master.RightPlayer, 7, 0, 5);
                GameObject distant = c.target(Master.RightPlayer, 10, 0, 5);
                float[] peakHeight = {0};
                Magic magic = water ? new WaterExplosionMagic() : new WindExplosionMagic();
                c.record(clips, water ? "water_explosion" : "wind_explosion", "area",
                        water ? "범위 피해·화상·공중 띄우기" : "범위 피해·밀치기",
                        water ? "Area damage, Burn and launch" : "Area damage and knockback", 4,
                        tick -> {
                            if (tick == 12) c.cast(magic, new Vector3(6, 0, 5));
                            peakHeight[0] = Math.max(peakHeight[0], target.getPosition().getY());
                        });
            }
        }
        for (boolean medium : new boolean[]{false, true}) {
            if (selectedName.equals("rock_blast")) try (PreviewCapture c = capture()) {
                GameObject remnant = new GameObject(Master.LeftPlayer, medium ? PrefabType.MediumRockRemnant : PrefabType.RockRemnant, new Vector3(6, 0, 5), c.context);
                GameObject target = c.target(Master.RightPlayer, 6.5f, 0, 5);
                c.record(clips, "rock_blast", medium ? "medium" : "small",
                        medium ? "중간 잔해 폭발·작은 잔해 잔존" : "작은 잔해 폭발·소모",
                        medium ? "Medium remnant bursts, small remnant remains" : "Small remnant bursts and is consumed", 4,
                        tick -> { if (tick == 12) c.cast(new RockBlastMagic(c.parameters), new Vector3(6, 0, 5)); });
            }
        }
        for (PrefabType type : new PrefabType[]{PrefabType.MiniRock, PrefabType.ThunderBird, PrefabType.WaterSlime}) {
            String name = switch (type) {
                case MiniRock -> "mini_rock_swarm";
                case ThunderBird -> "thunder_bird_swarm";
                default -> "water_slime_swarm";
            };
            if (selectedName.equals(name)) try (PreviewCapture c = capture()) {
                GameObject target = c.target(Master.RightPlayer, type == PrefabType.WaterSlime ? 9 : 6.5f, 0, 5);
                Magic magic = switch (type) {
                    case MiniRock -> new MiniRockSwarmMagic(c.parameters);
                    case ThunderBird -> new thunderBirdSwarmMagic(c.parameters);
                    default -> new WaterSlimeSwarmMagic(c.parameters);
                };
                c.record(clips, name, "ground", "무리 소환·지상 공격", "Swarm summon and ground attack", 6,
                        tick -> {
                            if (tick == 12) {
                                c.cast(magic, new Vector3(4, 0, 5));
                                c.stageSwarm(type, 4);
                            }
                        });
            }
        }
        if (selectedName.equals("water_slime_swarm")) try (PreviewCapture c = capture()) {
            GameObject target = c.target(Master.RightPlayer, 9, 0, 5);
            c.record(clips, "water_slime_swarm", "trail", "이동 경로 물 필드·젖음", "Water trail and Wet", 6,
                    tick -> {
                        if (tick == 12) {
                            c.cast(new WaterSlimeSwarmMagic(c.parameters), new Vector3(4, 0, 5));
                            c.stageSwarm(PrefabType.WaterSlime, 4);
                        }
                        // Place a passive victim onto a real emitted trail; movement/field logic is real.
                        if (tick == 55) c.context.getActiveGameObjects().stream()
                                .filter(o -> o.getType() == PrefabType.WaterField).findFirst()
                                .ifPresent(field -> target.setPosition(new Vector3(field.getPosition())));
                    });
        }
        if (selectedName.equals("thunder_bird_swarm")) try (PreviewCapture c = capture()) {
            c.cast(new thunderBirdSwarmMagic(c.parameters), new Vector3(5, 0, 5));
            c.stageSwarm(PrefabType.ThunderBird, 5);
            GameObject dying = c.pending(PrefabType.ThunderBird);
            c.record(clips, "thunder_bird_swarm", "death_energy", "사망 낙하·아군 에너지 흡수",
                    "Death fall and ally absorption", 7,
                    tick -> { if (tick == 20) dying.getComponent(Mob.class).onDamaged(new AttackInfo(10000, com.wordonline.server.game.domain.magic.ElementType.NONE)); });
        }
    }
}
