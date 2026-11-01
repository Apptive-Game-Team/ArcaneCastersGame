package com.wordonline.server.tutorial;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.server.service.ServerUrlProvider;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
@EnableConfigurationProperties(TutorialProperties.class)
public class TutorialService {
    private final Parameters parameters;
    private final Map<String, PrefabInitializer> prefabs;
    private final DatabaseMagicParser parser;
    private final TutorialProperties limits;
    private final ServerUrlProvider urls;
    private final ServerStatusService status;
    private final Map<String, TutorialBattle> battles = new HashMap<>();

    public TutorialService(Parameters parameters, Map<String, PrefabInitializer> prefabs, DatabaseMagicParser parser,
            TutorialProperties limits, ServerUrlProvider urls, ServerStatusService status) {
        this.parameters = parameters;
        this.prefabs = prefabs;
        this.parser = parser;
        this.limits = limits;
        this.urls = urls;
        this.status = status;
    }

    public synchronized Ready create(long ownerId, String faction, String requestId) {
        if (ownerId <= 0 || !Set.of("HELL", "NATURE").contains(faction == null ? "" : faction))
            throw TutorialBattle.error(HttpStatus.BAD_REQUEST, "Invalid tutorial owner or faction");
        try { UUID.fromString(requestId); }
        catch (RuntimeException invalid) { throw TutorialBattle.error(HttpStatus.BAD_REQUEST, "Invalid request id"); }
        cleanup();
        for (var battle : battles.values()) {
            if (battle.ownerId() == ownerId) {
                if (!battle.faction().equals(faction)) throw TutorialBattle.error(HttpStatus.CONFLICT, "Faction changed");
                return ready(battle);
            }
        }
        if (status.getCurrentState() != ServerState.ACTIVE)
            throw TutorialBattle.error(HttpStatus.SERVICE_UNAVAILABLE, "Server is not accepting tutorials");
        if (battles.size() >= limits.maxBattles())
            throw TutorialBattle.error(HttpStatus.TOO_MANY_REQUESTS, "Tutorial capacity reached");
        Map<String, Magic> magics = new HashMap<>();
        var names = new HashSet<>(TutorialBattle.HELL);
        names.addAll(TutorialBattle.NATURE);
        names.addAll(List.of("meteor_shower", "tornado_strike"));
        for (String name : names) {
            Magic magic = parser.parseMagicForBot(name);
            if (magic == null || magic.id <= 0)
                throw TutorialBattle.error(HttpStatus.SERVICE_UNAVAILABLE, "Tutorial magic unavailable");
            magics.put(name, magic);
        }
        String id = "tutorial-" + UUID.randomUUID();
        var battle = new TutorialBattle(id, ownerId, faction, Instant.now().plus(limits.lifetime()), parameters,
                prefabs, Map.copyOf(magics), parser, limits, System::nanoTime);
        battles.put(id, battle);
        return ready(battle);
    }

    private Ready ready(TutorialBattle battle) {
        return new Ready(battle.id(), battle.ownerId(), battle.faction(), urls.getServerUrl(), battle.expiresAt());
    }

    public synchronized TutorialBattle owned(String id, long ownerId) {
        var battle = battles.get(id);
        if (battle == null) throw TutorialBattle.error(HttpStatus.NOT_FOUND, "Tutorial not found");
        if (battle.ownerId() != ownerId) throw TutorialBattle.error(HttpStatus.FORBIDDEN, "Tutorial owner mismatch");
        if (!battle.expiresAt().isAfter(Instant.now())) {
            battles.remove(id);
            battle.close();
            throw TutorialBattle.error(HttpStatus.GONE, "Tutorial expired");
        }
        return battle;
    }

    public synchronized void close(String id, long ownerId) {
        var battle = owned(id, ownerId);
        battles.remove(id);
        battle.close();
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void cleanup() {
        Instant now = Instant.now();
        var iterator = battles.values().iterator();
        while (iterator.hasNext()) {
            var battle = iterator.next();
            if (!battle.expiresAt().isAfter(now)) {
                iterator.remove();
                battle.close();
            }
        }
    }

    @PreDestroy public synchronized void closeAll() {
        battles.values().forEach(TutorialBattle::close);
        battles.clear();
    }

    public record Ready(String battleId, long ownerId, String faction, String server, Instant expiresAt) { }
}
