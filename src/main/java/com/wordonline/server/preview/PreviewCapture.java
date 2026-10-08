package com.wordonline.server.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.*;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.physic.*;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.dto.*;
import com.wordonline.server.game.service.*;
import com.wordonline.server.game.service.system.*;
import com.wordonline.server.game.util.SimplePhysics;
import java.util.*;
import java.util.function.IntConsumer;

final class PreviewCapture implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final float DT = 1f / GameLoop.FPS;
    final PreviewParameters parameters;
    final GameContext context;
    final Map<String, Magic> magics;
    private final PreviewProperties limits;
    private final List<GameObject> fixtureTargets = new ArrayList<>();
    private final Map<Integer, Integer> ids = new HashMap<>();
    private boolean creatingTarget;

    PreviewCapture(Map<String, Double> inputs, Map<String, Class<? extends PrefabInitializer>> prefabs,
                   Map<String, Magic> magics, PreviewProperties limits) {
        parameters = new PreviewParameters(inputs);
        this.magics = magics;
        this.limits = limits;
        var data = new GameSessionData(new PlayerData(new ManaCharger(parameters)), new PlayerData(new ManaCharger(parameters)));
        context = new GameContext(null, data, parameters, null, null) {
            @Override public boolean suppressesDeathFields() { return true; }
            @Override public boolean initializePrefab(GameObject object) {
                if (creatingTarget) {
                    object.addCollider(new CircleCollider(object, 0.35f, true));
                    object.addComponent(new PassiveTarget(object));
                    object.addComponent(new RigidBody(object, 10));
                    if (object.getPosition().getY() == 0) object.addComponent(new ZPhysics(object));
                    object.addComponent(new CommonEffectReceiver(object));
                } else if (object.getType() != PrefabType.Player) {
                    Class<? extends PrefabInitializer> type = prefabs.get(object.getType().getBeanName());
                    if (type == null) throw new IllegalArgumentException("Missing preview prefab: " + object.getType());
                    PreviewConstruction.create(type, parameters).initialize(object);
                }
                return true;
            }
            @Override public void createGameObject(GameObject object) {
                if (data.gameObjects.size() + data.gameObjectsToAdd.size() >= limits.maxObjects())
                    throw new IllegalStateException("Preview object limit exceeded");
                super.createGameObject(object);
            }
            @Override public void onObjectInitializationFailed(GameObject object, RuntimeException error) {
                throw new IllegalStateException("Preview object failed to initialize: " + object.getType(), error);
            }
        };
        context.setObjectsInfoDtoBuilder(new ObjectsInfoDtoBuilder(context));
        context.setPhysics(new SimplePhysics(data.gameObjects));
        context.setDeltaTime(DT);
        new GameObject(Master.LeftPlayer, PrefabType.Player, new Vector3(2, 0, 5), context);
    }

    Magic magic(String name) {
        Magic source = magics.get(name);
        if (source == null) throw new IllegalArgumentException("Unknown preview magic: " + name);
        Magic magic = PreviewConstruction.create(source.getClass(), parameters);
        magic.id = source.id;
        magic.name = source.name;
        magic.element = source.element;
        return magic;
    }

    GameObject target(Master master, float x, float y, float z) {
        creatingTarget = true;
        try {
            GameObject object = new GameObject(master, PrefabType.ElectricSlime, new Vector3(x, y, z), context);
            fixtureTargets.add(object);
            return object;
        } finally { creatingTarget = false; }
    }

    List<GameObject> cast(Magic magic, Vector3 position) {
        Set<GameObject> before = new HashSet<>(context.getGameSessionData().gameObjectsToAdd);
        magic.run(context, Master.LeftPlayer, position);
        return context.getGameSessionData().gameObjectsToAdd.stream().filter(object -> !before.contains(object)).toList();
    }

    GameObject pending(PrefabType type) {
        return context.getGameSessionData().gameObjectsToAdd.stream().filter(o -> o.getType() == type).findFirst().orElseThrow();
    }

    void stageSwarm(PrefabType type, float x) {
        List<GameObject> swarm = context.getGameSessionData().gameObjectsToAdd.stream().filter(o -> o.getType() == type).toList();
        for (int i = 0; i < swarm.size(); i++) {
            swarm.get(i).getPosition().setX(x);
            swarm.get(i).getPosition().setZ(5 + (i - (swarm.size() - 1) / 2f) * 0.8f);
        }
    }

    private int id(int original) { return ids.computeIfAbsent(original, ignored -> ids.size() + 1); }

    void record(Map<String, ObjectNode> clips, String magic, String scenarioId, String ko, String en,
                float duration, IntConsumer action) {
        int ticks = Math.round(duration / DT);
        if (ticks <= 0 || ticks > limits.maxTicks()) throw new IllegalArgumentException("Preview tick limit exceeded");
        ObjectNode clip = clips.computeIfAbsent(magic, name -> {
            ObjectNode node = JSON.createObjectNode();
            node.put("version", 2).put("magic", name).put("frameDuration", DT);
            node.put("source", "server; current parameter snapshot; production simulation; passive targets");
            node.putArray("scenarios");
            return node;
        });
        ObjectNode scenario = clip.withArray("scenarios").addObject();
        scenario.put("id", scenarioId).put("labelKo", ko).put("labelEn", en).put("duration", duration);
        var frames = scenario.putArray("frames");
        var lifecycle = new GameObjectAddRemoteSystem();
        var reset = new GameObjectStateInitialSystem();
        var physics = new PhysicSystem();
        lifecycle.update(context);
        int recordedBytes = 0;
        for (int tick = 0; tick < ticks; tick++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            reset.update(context);
            action.accept(tick);
            for (GameObject object : List.copyOf(context.getActiveGameObjects())) object.update();
            physics.update(context);
            lifecycle.update(context);
            for (GameObject object : context.getActiveGameObjects()) object.applyUpdate();
            ObjectNode frame = frames.addObject();
            frame.put("time", tick * (double) DT);
            JsonNode objects = JSON.valueToTree(context.getObjectsInfoDto());
            for (String key : new String[]{"create", "update"}) {
                for (JsonNode entry : objects.path(key)) ((ObjectNode)entry).put("id", id(entry.path("id").asInt()));
            }
            for (JsonNode projectile : objects.path("projectile")) {
                for (String key : new String[]{"start", "end"}) {
                    JsonNode endpoint = projectile.path(key);
                    if (endpoint.has("id")) ((ObjectNode)endpoint).put("id", id(endpoint.path("id").asInt()));
                }
            }
            frame.set("objects", objects);
            var events = frame.putArray("events");
            for (var event : context.drainEvents()) {
                ObjectNode node = JSON.valueToTree(event);
                node.put("actorId", id(event.actorId())).put("targetId", id(event.targetId()));
                events.add(node);
            }
            recordedBytes += frame.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (recordedBytes > limits.maxClipBytes()) throw new IllegalStateException("Preview frame byte limit exceeded");
            context.incrementFrameNum();
        }
        var targets = scenario.putArray("fixtureTargetIds");
        fixtureTargets.stream().map(object -> ids.get(object.getId())).filter(Objects::nonNull).sorted().forEach(targets::add);
        scenario.set("parameters", JSON.valueToTree(parameters.reads));
    }

    @Override public void close() {
        // No onDestroy callbacks: cleanup must not spawn death/trail objects into a finished capture.
        context.getGameSessionData().gameObjects.clear();
        context.getGameSessionData().gameObjectsToAdd.clear();
        context.drainEvents();
    }

    private static final class PassiveTarget extends Mob implements Collidable {
        PassiveTarget(GameObject object) { super(object, 10000, 0); }
        @Override public void start() { }
        @Override public void onDestroy() { }
        @Override public void onDeath() { gameObject.destroy(); }
        @Override public void onCollisionWithEnemy(GameObject other) { }
    }
}
