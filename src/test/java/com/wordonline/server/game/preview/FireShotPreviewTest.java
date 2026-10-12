package com.wordonline.server.game.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.implement.shoot.FireShotMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.domain.object.prefab.implement.fire.FireShotPrefabInitializer;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.*;
import com.wordonline.server.game.service.system.*;
import com.wordonline.server.game.util.SimplePhysics;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Offline example capture: real spell/physics, stationary targets, no Spring/DB/network. */
class FireShotPreviewTest {
    @Test
    void recordsFlightAndDamageConfirmedSplash() throws Exception {
        ParameterService service = mock(ParameterService.class);
        // Explicit illustrative fixture, from database V001: not a live-balance assertion.
        when(service.getValue("fire_shot", "speed")).thenReturn(8d);
        when(service.getValue("fire_shot", "radius")).thenReturn(0.5d);
        when(service.getValue("fire_shot", "damage")).thenReturn(100d);
        Parameters parameters = new Parameters(service);
        GameSessionData data = new GameSessionData(new PlayerData(null), new PlayerData(null));
        GameContext context = new GameContext(null, data, parameters, null, null);
        context.setObjectsInfoDtoBuilder(new ObjectsInfoDtoBuilder(context));
        context.setPhysics(new SimplePhysics(data.gameObjects));
        context.setDeltaTime(0.05f);
        var lifecycle = new GameObjectAddRemoteSystem();
        var reset = new GameObjectStateInitialSystem();
        var physics = new PhysicSystem();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode recording = mapper.createObjectNode();
        recording.put("version", 1);
        recording.put("magic", "fire_shot");
        recording.put("frameDuration", 0.05);
        recording.put("duration", 3.0);
        recording.put("source", "FireShotPreviewTest; real FireShotMagic/Shot/PhysicSystem; stationary fixture targets");
        recording.putObject("parameters").put("speed", 8).put("radius", 0.5).put("damage", 100);
        var frames = recording.putArray("frames");
        Map<Integer, Integer> stableIds = new LinkedHashMap<>();

        // Mock only prefab lookup, keeping the production initializer for the spell itself.
        try (MockedStatic<PrefabProvider> provider = mockStatic(PrefabProvider.class)) {
            provider.when(() -> PrefabProvider.get(PrefabType.FireShot))
                    .thenReturn(new FireShotPrefabInitializer(parameters));
            provider.when(() -> PrefabProvider.get(PrefabType.Player)).thenReturn(new PrefabInitializer(PrefabType.Player) {
                @Override public void initialize(GameObject object) { }
            });
            provider.when(() -> PrefabProvider.get(PrefabType.ElectricSlime)).thenReturn(new PrefabInitializer(PrefabType.ElectricSlime) {
                @Override public void initialize(GameObject object) {
                    object.addCollider(new CircleCollider(object, 0.35f, true));
                    object.addComponent(new Target(object));
                }
            });
            new GameObject(Master.LeftPlayer, PrefabType.Player, new Vector3(2, 0, 5), context);
            GameObject direct = new GameObject(Master.RightPlayer, PrefabType.ElectricSlime, new Vector3(8, 0, 5), context);
            GameObject adjacent = new GameObject(Master.RightPlayer, PrefabType.ElectricSlime, new Vector3(8, 0, 5.7f), context);
            GameObject distant = new GameObject(Master.RightPlayer, PrefabType.ElectricSlime, new Vector3(10, 0, 7), context);
            lifecycle.update(context);
            GameObject shot = null;
            int impactCount = 0;
            int movingFrames = 0;
            for (int tick = 0; tick < 60; tick++) {
                reset.update(context);
                if (tick == 12) {
                    new FireShotMagic().run(context, Master.LeftPlayer, new Vector3(8, 0, 5));
                    shot = data.gameObjectsToAdd.getFirst();
                }
                // Fail loudly rather than ComponentUpdateSystem's production catch-and-log.
                for (GameObject object : context.getActiveGameObjects()) object.update();
                int damageBefore = direct.getComponent(Target.class).hits;
                physics.update(context);
                var frame = frames.addObject();
                frame.put("time", tick * 0.05);
                // Capture real outgoing object DTOs; normalize process-global ids for reproducibility.
                var objects = mapper.valueToTree(context.getObjectsInfoDto());
                for (String key : new String[]{"create", "update"}) {
                    for (var entry : objects.path(key)) {
                        int id = entry.path("id").asInt();
                        ((ObjectNode) entry).put("id", stableIds.computeIfAbsent(id, ignored -> stableIds.size() + 1));
                    }
                }
                frame.set("objects", objects);
                if (direct.getComponent(Target.class).hits > damageBefore) {
                    assertThat(shot).isNotNull();
                    frame.set("impact", mapper.valueToTree(shot.getPosition()));
                    impactCount++;
                }
                if (shot != null && !shot.isDestroyed() && tick > 12) movingFrames++;
                lifecycle.update(context);
            }
            assertThat(shot).isNotNull();
            assertThat(shot.getStatus()).isEqualTo(Status.Destroyed);
            assertThat(movingFrames).isGreaterThan(5);
            assertThat(impactCount).isEqualTo(1);
            assertThat(direct.getComponent(Target.class).hits).isEqualTo(1);
            assertThat(adjacent.getComponent(Target.class).hits).isEqualTo(1);
            assertThat(distant.getComponent(Target.class).hits).isZero();
            assertThat(frames.findValues("type").stream().anyMatch(n -> n.asText().equals("FireShot"))).isTrue();
        }
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(recording);
        assertThat(mapper.readTree(json).path("frames").size()).isEqualTo(60);
        if (Boolean.getBoolean("preview.export")) {
            Path output = Path.of("build/previews/fire_shot.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, json + "\n");
        }
    }

    private static final class Target extends Component implements Damageable, Collidable {
        private int hits;
        private Target(GameObject object) { super(object); }
        @Override public void onDamaged(AttackInfo info) { hits++; }
        @Override public void onDamaged(AttackInfo info, float delay) { onDamaged(info); }
        @Override public void onCollisionWithEnemy(GameObject other) { }
        @Override public void start() { }
        @Override public void update() { }
        @Override public void onDestroy() { }
    }
}
