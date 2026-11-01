package com.wordonline.server.tutorial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.*;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.CombatDeathListener;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.SnapshotObjectDto;
import com.wordonline.server.game.service.*;
import com.wordonline.server.game.service.system.*;
import com.wordonline.server.game.util.SimplePhysics;
import com.wordonline.server.game.util.SnapshotMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;

/** 전투 수치와 판정은 기존 엔진을 사용하고, 학습 구간과 일시정지만 별도로 관리한다. */
public final class TutorialBattle implements AutoCloseable {
    static final List<String> HELL = List.of("fire_shot", "ember_spirit_swarm", "towerback", "rock_golem", "magma_explosion");
    static final List<String> NATURE = List.of("chain_lightning", "water_slime_swarm", "vine_spirit", "tree_golem", "water_explosion");
    static final List<String> STAGES = List.of("shot", "meteor", "tornado", "anti_air", "formation", "explosion", "final");
    private static final int TICK_RATE = 20;
    private static final float DT = 1f / TICK_RATE;
    private static final long TICK_NANOS = 1_000_000_000L / TICK_RATE;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String id;
    private final long ownerId;
    private final String faction;
    private final Instant expiresAt;
    private final Parameters parameters;
    private final Map<String, PrefabInitializer> prefabs;
    private final Map<String, Magic> magics;
    private final DatabaseMagicParser parser;
    private final TutorialProperties limits;
    private final LongSupplier clock;
    private final List<String> starters;
    private final Set<Integer> enemies = new HashSet<>();
    private final Set<Integer> defeated = new HashSet<>();
    private final Set<String> casts = new HashSet<>();
    private final LinkedHashMap<String, Command> commands = new LinkedHashMap<>();
    private final Deque<ObjectNode> history = new ArrayDeque<>();
    private int historyBytes;
    private GameContext context;
    private com.wordonline.server.game.dto.frame.FrameInfoDto manaFrame;
    private int stage;
    private int revision;
    private int frame;
    private int stageTicks;
    private int wave;
    private int castCount;
    private String status = "RUNNING";
    private boolean formationReady;
    private long lastTime;
    private long remainder;

    public TutorialBattle(String id, long ownerId, String faction, Instant expiresAt, Parameters parameters,
            Map<String, PrefabInitializer> prefabs, Map<String, Magic> magics, DatabaseMagicParser parser,
            TutorialProperties limits, LongSupplier clock) {
        if (!Set.of("HELL", "NATURE").contains(faction)) throw error(HttpStatus.BAD_REQUEST, "Unknown faction");
        this.id = id;
        this.ownerId = ownerId;
        this.faction = faction;
        this.expiresAt = expiresAt;
        this.parameters = parameters;
        this.prefabs = prefabs;
        this.magics = magics;
        this.parser = parser;
        this.limits = limits;
        this.clock = clock;
        starters = faction.equals("HELL") ? HELL : NATURE;
        reset();
    }

    public long ownerId() { return ownerId; }
    public String faction() { return faction; }
    public Instant expiresAt() { return expiresAt; }
    public String id() { return id; }

    private void reset() {
        disposeContext();
        enemies.clear();
        defeated.clear();
        casts.clear();
        castCount = 0;
        formationReady = false;
        wave = 0;
        stageTicks = 0;
        remainder = 0;
        lastTime = clock.getAsLong();
        status = "RUNNING";
        history.clear();
        historyBytes = 0;
        var left = new PlayerData(new ManaCharger(parameters));
        var right = new PlayerData(new ManaCharger(parameters));
        left.manaCharger.initMaxMana();
        right.manaCharger.initMaxMana();
        var data = new GameSessionData(left, right);
        manaFrame = new com.wordonline.server.game.dto.frame.FrameInfoDto(0, null, null, data, List.of());
        context = new GameContext(null, data, parameters, new MagicInputHandler(parser), parser) {
            @Override public boolean initializePrefab(GameObject object) {
                var initializer = prefabs.get(object.getType().getBeanName());
                if (initializer == null) throw new IllegalStateException("Missing tutorial prefab: " + object.getType());
                initializer.initialize(object);
                return true;
            }
            @Override public void createGameObject(GameObject object) {
                if (data.gameObjects.size() + data.gameObjectsToAdd.size() >= limits.maxObjects())
                    throw new IllegalStateException("Tutorial object limit exceeded");
                super.createGameObject(object);
            }
            @Override public void onObjectInitializationFailed(GameObject object, RuntimeException failure) {
                throw new IllegalStateException("Tutorial prefab initialization failed", failure);
            }
        };
        context.setResultChecker(new ResultChecker(null));
        context.setObjectsInfoDtoBuilder(new ObjectsInfoDtoBuilder(context));
        context.setPhysics(new SimplePhysics(data.gameObjects));
        context.setDeltaTime(DT);
        context.setTickRate(TICK_RATE);
        context.setObjectInitializer(object -> {
            if (object.getMaster() == Master.RightPlayer && object.getType() != PrefabType.Player
                    && object.getComponent(Mob.class) != null) {
                enemies.add(object.getId());
                object.addComponent(new EnemyDeath(object));
                object.flushComponents();
            }
        });
        new GameObject(Master.LeftPlayer, PrefabType.Player, new Vector3(1, 0, 5), context);
        new GameObject(Master.RightPlayer, PrefabType.Player, new Vector3(17, 0, 5), context);
        new GameObject(Master.None, PrefabType.Wall, new Vector3(0, 0, 0), context);
        left.mana = ManaCharger.MAX_MANA;
        if (stage != 4) spawnWave();
        new GameObjectAddRemoteSystem().update(context);
        capture();
    }

    private List<String> allowed() {
        return switch (stage) {
            case 0 -> List.of(starters.get(0));
            case 1 -> castCount == 0 ? List.of("meteor_shower") : List.of("meteor_shower", starters.get(0));
            case 2 -> List.of("tornado_strike");
            case 3 -> List.of(starters.get(2));
            case 4 -> formationReady ? List.of(starters.get(3), starters.get(1)) : List.of(starters.get(3));
            case 5 -> List.of(starters.get(4));
            default -> starters;
        };
    }

    private void spawnWave() {
        if (stage == 0) {
            if (faction.equals("HELL")) spawn(PrefabType.VineSpirit, 1, 8, 5, 0);
            else spawn(PrefabType.WaterSlime, 5, 8, 5, 0.35f);
        } else if (stage == 1) spawn(PrefabType.WaterSlime, 8, 5.5f, 5, 0.4f);
        else if (stage == 2) spawn(PrefabType.MiniRock, 6, 9, 5, 0.3f);
        else if (stage == 3) spawn(PrefabType.BubbleSpirit, 1, 9, 5, 0);
        else if (stage == 4) spawn(PrefabType.MiniRock, 2, 10, 5, 0.5f);
        else if (stage == 5 || wave == 2) {
            spawn(faction.equals("HELL") ? PrefabType.SeedSpirit : PrefabType.EmberSpirit,
                    stage == 5 ? (faction.equals("HELL") ? 8 : 6) : (faction.equals("HELL") ? 6 : 4), 8, 5, 0.35f);
        } else if (wave == 0) spawn(PrefabType.MiniRock, 2, 10, 5, 0.5f);
        else spawn(PrefabType.BubbleSpirit, 1, 9, 5, 0);
    }

    private void spawn(PrefabType prefab, int count, float x, float z, float spacing) {
        for (int i = 0; i < count; i++) {
            float height = prefab == PrefabType.BubbleSpirit ? GameConfig.AERIAL_MOB_INIT_HEIGHT : 0;
            new GameObject(Master.RightPlayer, prefab,
                    new Vector3(x + (i % 3) * spacing, height, z + (i / 3 - (count - 1) / 6f) * spacing), context);
        }
    }

    public synchronized void command(Command request) {
        if (status.equals("CLOSED")) throw error(HttpStatus.GONE, "Tutorial closed");
        advance();
        if (request == null || request.commandId() == null || request.commandId().length() > 64
                || request.commandId().isBlank()) throw error(HttpStatus.BAD_REQUEST, "Invalid command id");
        Command previous = commands.get(request.commandId());
        if (previous != null) {
            if (!JSON.valueToTree(previous).equals(JSON.valueToTree(request)))
                throw error(HttpStatus.CONFLICT, "Command id reused");
            return;
        }
        if (commands.size() >= 1024) throw error(HttpStatus.TOO_MANY_REQUESTS, "Tutorial command limit reached");
        if (request.revision() != revision) throw error(HttpStatus.CONFLICT, "Tutorial step changed");
        switch (request.action() == null ? "" : request.action()) {
            case "CAST" -> cast(request.magic(), request.position());
            case "NEXT" -> {
                if (!status.equals("CLEARED")) throw error(HttpStatus.CONFLICT, "Step is not cleared");
                stage++;
                revision++;
                reset();
            }
            case "RETRY" -> {
                if (!status.equals("FAILED")) throw error(HttpStatus.CONFLICT, "Step has not failed");
                revision++;
                reset();
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "Unknown tutorial command");
        }
        commands.put(request.commandId(), request);
    }

    private void cast(String name, Vector3 position) {
        if (!status.equals("RUNNING"))
            throw error(HttpStatus.CONFLICT, "Tutorial step is not running");
        if (!allowed().contains(name))
            throw error(HttpStatus.BAD_REQUEST, "Magic is not available in this step");
        if (position == null || !Float.isFinite(position.getX()) || !Float.isFinite(position.getY())
                || !Float.isFinite(position.getZ()) || position.getY() != 0 || position.getX() < 0
                || position.getX() > 18 || position.getZ() < 0 || position.getZ() > 10)
            throw error(HttpStatus.BAD_REQUEST, "Invalid field position");
        Magic magic = magics.get(name);
        if (magic == null || magic.id <= 0) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Tutorial magic unavailable");
        Vector3 origin = new Vector3(1, 0, 5);
        double range = parameters.getValue(name, "range");
        if (origin.distance(position) > range + 0.01)
            throw error(HttpStatus.BAD_REQUEST, "Target is out of range");
        Vector3 free = CastPlacement.resolveSpot(context, magic, new Vector3(position), origin, range)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Cannot place here"));
        int cost = (int) parameters.getValue(name, "mana_cost");
        if (cost < 0 || !context.getGameSessionData().leftPlayerData.spendMana(cost))
            throw error(HttpStatus.BAD_REQUEST, "Insufficient mana");
        try {
            magic.run(context, Master.LeftPlayer, origin, free);
            casts.add(name);
            castCount++;
            if (stage == 4 && enemies.isEmpty()) spawnWave();
            new GameObjectAddRemoteSystem().update(context);
            capture();
        } catch (RuntimeException failure) {
            status = "FAILED";
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Tutorial cast failed");
        }
    }

    private void advance() {
        long now = clock.getAsLong();
        long elapsed = Math.max(0, Math.min(now - lastTime, 500_000_000L));
        lastTime = now;
        if (!status.equals("RUNNING")) { remainder = 0; return; }
        // 첫 조작을 읽는 동안 적과 마나 시계가 먼저 진행하지 않게 한다.
        if (castCount == 0) { remainder = 0; return; }
        remainder += elapsed;
        while (remainder >= TICK_NANOS && status.equals("RUNNING")) {
            remainder -= TICK_NANOS;
            try { tick(); }
            catch (RuntimeException failure) { status = "FAILED"; }
        }
    }

    private void tick() {
        stageTicks++;
        context.incrementFrameNum();
        new GameObjectStateInitialSystem().update(context);
        for (GameObject object : List.copyOf(context.getActiveGameObjects())) object.update();
        new PhysicSystem().update(context);
        new GameObjectAddRemoteSystem().update(context);
        for (GameObject object : context.getActiveGameObjects()) object.applyUpdate();
        var left = context.getGameSessionData().leftPlayerData;
        // 기존 ManaCharger가 소수·충전 간격·최대 마나를 처리한다.
        left.manaCharger.chargeMana(left, manaFrame, DT);
        if (stage == 4 && !formationReady) {
            PrefabType tank = faction.equals("HELL") ? PrefabType.RockGolem : PrefabType.TreeGolem;
            formationReady = context.getActiveGameObjects().stream().anyMatch(object -> {
                Mob mob = object.getComponent(Mob.class);
                return object.getMaster() == Master.LeftPlayer && object.getType() == tank
                        && mob != null && mob.getHp() < mob.getMaxHp();
            });
        }
        if (left.hp <= 0 || stageTicks >= 120 * 20) status = "FAILED";
        else if (!enemies.isEmpty() && defeated.containsAll(enemies) && castCount > 0
                && context.getActiveGameObjects().stream().noneMatch(object ->
                        object.getMaster() == Master.RightPlayer && object.getType() != PrefabType.Player
                                && object.getComponent(Mob.class) != null)
                && (stage != 4 || casts.contains(starters.get(1)))) {
            if (stage == 6 && wave < 2) {
                wave++;
                spawnWave();
                new GameObjectAddRemoteSystem().update(context);
            } else status = stage == 6 ? "COMPLETED" : "CLEARED";
        }
        capture();
    }

    private void capture() {
        ObjectNode entry = JSON.createObjectNode();
        entry.put("frame", ++frame);
        entry.put("tick", stageTicks);
        entry.set("objects", JSON.valueToTree(context.getObjectsInfoDto()));
        entry.set("events", JSON.valueToTree(context.drainEvents()));
        history.addLast(entry);
        historyBytes += entry.toString().length() * 2;
        while (history.size() > 100 || historyBytes > 1_000_000) {
            historyBytes -= history.removeFirst().toString().length() * 2;
        }
    }

    public synchronized State state(int afterFrame) {
        if (status.equals("CLOSED")) throw error(HttpStatus.GONE, "Tutorial closed");
        advance();
        var snapshot = context.getActiveGameObjects().stream().map(SnapshotMapper::toDto).toList();
        var available = allowed();
        String recommended = stage == 1 && castCount > 0 ? starters.get(0) : stage == 6 ? null : stage == 4
                ? (formationReady ? starters.get(1) : starters.get(3)) : available.getFirst();
        List<Long> cards = available.stream().map(magics::get).filter(Objects::nonNull).map(magic -> magic.id).toList();
        GameObject target = context.getActiveGameObjects().stream().filter(object -> object.getMaster() == Master.RightPlayer
                && object.getType() != PrefabType.Player && object.getComponent(Mob.class) != null && !object.isDying()).findFirst().orElse(null);
        return new State(id, ownerId, faction, revision, STAGES.get(stage), status, wave,
                context.getGameSessionData().leftPlayerData.mana, cards, recommended,
                target == null ? 6 : target.getPosition().getX(), target == null ? 5 : target.getPosition().getZ(),
                Math.max(0, 120 - stageTicks / 20),
                history.stream().filter(entry -> entry.path("frame").asInt() > afterFrame).map(entry -> (JsonNode) entry).toList(),
                snapshot, frame, stageTicks, TICK_RATE);
    }

    private final class EnemyDeath extends Component implements CombatDeathListener {
        EnemyDeath(GameObject object) { super(object); }
        @Override public void onCombatDeath() { defeated.add(gameObject.getId()); }
        @Override public void start() { }
        @Override public void update() { }
        @Override public void onDestroy() { }
    }

    private void disposeContext() {
        if (context == null) return;
        var data = context.getGameSessionData();
        // 구간 교체와 종료는 전투 사망이 아니므로 사망 소환물을 만들지 않는다.
        for (var object : List.copyOf(data.gameObjects)) object.discard();
        for (var object : List.copyOf(data.gameObjectsToAdd)) object.discard();
        data.gameObjects.clear();
        data.gameObjectsToAdd.clear();
        context.drainEvents();
    }

    @Override public synchronized void close() { status = "CLOSED"; disposeContext(); history.clear(); commands.clear(); }
    static ResponseStatusException error(HttpStatus status, String reason) { return new ResponseStatusException(status, reason); }
    public record Command(String commandId, int revision, String action, String magic, Vector3 position) { }
    public record State(String battleId, long ownerId, String faction, int revision, String stage, String status, int wave,
            int mana, List<Long> cards, String recommended, float targetX, float targetZ, int remainingSeconds, List<JsonNode> frames,
            List<SnapshotObjectDto> snapshot, int lastFrame, int tick, int tickRate) { }
}
