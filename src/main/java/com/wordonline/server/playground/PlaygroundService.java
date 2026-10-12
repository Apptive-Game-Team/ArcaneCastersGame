package com.wordonline.server.playground;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.server.service.ServerUrlProvider;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.service.SessionService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "playground.enabled", havingValue = "true", matchIfMissing = true)
public class PlaygroundService {
    private final SessionService sessions;
    private final DatabaseMagicParser magics;
    private final ServerUrlProvider urls;
    private final ServerStatusService status;
    private final com.wordonline.server.game.repository.MagicRepository catalog;

    public java.util.List<com.wordonline.server.debug.dto.DebugMagicInfoDto> magics() {
        return catalog.getAllMagic().stream().map(magic ->
                new com.wordonline.server.debug.dto.DebugMagicInfoDto(magic.id(), magic.name())).toList();
    }

    public PlaygroundInfo create(long ownerId) {
        if (ownerId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner.");
        if (status.getCurrentState() != ServerState.ACTIVE)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Server is not accepting sessions.");
        String id = "playground-" + UUID.randomUUID();
        sessions.createSession(new SessionDto(id, ownerId, -1L, SessionType.Playground, null, List.of(), List.of()));
        PlaygroundLoop loop = ownedLoop(id, ownerId);
        if (!loop.awaitStart(Duration.ofSeconds(2))) {
            loop.close();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Playground did not start.");
        }
        String url = urls.getServerUrl();
        return new PlaygroundInfo(id, url, url + "/ws", ownerId, loop.getExpiresAt());
    }

    public PlaygroundLoop ownedLoop(String id, long ownerId) {
        var session = sessions.getSessionObject(id);
        if (session == null || !(session.getGameLoop() instanceof PlaygroundLoop loop))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Playground not found.");
        if (session.getLeftUserId() != ownerId)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Playground belongs to another developer.");
        if (loop.isExpired()) throw new ResponseStatusException(HttpStatus.GONE, "Playground expired.");
        return loop;
    }

    public PlaygroundReply cast(PlaygroundLoop loop, long magicId, Master master, Vector3 position) {
        requireSide(master);
        if (magicId <= 0 || position == null || !Float.isFinite(position.getX())
                || !Float.isFinite(position.getY()) || !Float.isFinite(position.getZ())
                || position.getX() < 0 || position.getX() > 18 || position.getZ() < 0 || position.getZ() > 10)
            return loop.reply(false, "Invalid magic or field position.");
        var magic = magics.parseMagicForBot(magicId);
        if (magic == null) return loop.reply(false, "Magic is not registered.");
        magic.run(loop.getGameContext(), master, new Vector3(position));
        return loop.reply(true, "Magic cast.");
    }

    public static void requireSide(Master side) {
        if (side != Master.LeftPlayer && side != Master.RightPlayer)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select LeftPlayer or RightPlayer.");
    }
}
