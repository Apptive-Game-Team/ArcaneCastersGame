package com.wordonline.server.playground;

import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.wordonline.server.auth.domain.PrincipalDetails;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.SnapshotResponseDto;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "playground.enabled", havingValue = "true", matchIfMissing = true)
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'WORDONLINE_ADMIN')")
@RequestMapping("/api/dev/playgrounds")
public class PlaygroundController {
    private final PlaygroundService service;

    @GetMapping("/{id}/magics")
    public java.util.List<com.wordonline.server.debug.dto.DebugMagicInfoDto> magics(
            @PathVariable String id, @AuthenticationPrincipal PrincipalDetails principal) {
        service.ownedLoop(id, principal.memberId);
        return service.magics();
    }

    @PostMapping("/{id}/cast")
    public CompletableFuture<PlaygroundReply> cast(@PathVariable String id,
            @AuthenticationPrincipal PrincipalDetails principal, @RequestBody Cast request) {
        var loop = service.ownedLoop(id, principal.memberId);
        PlaygroundService.requireSide(request.master());
        Vector3 target = request.position() == null ? null : new Vector3(request.position());
        return loop.command("playgroundCast", () -> service.cast(loop, request.magicId(), request.master(), target));
    }

    @PostMapping("/{id}/clear")
    public CompletableFuture<PlaygroundReply> clear(@PathVariable String id,
            @AuthenticationPrincipal PrincipalDetails principal, @RequestBody Clear request) {
        var loop = service.ownedLoop(id, principal.memberId);
        if (request.master() != Master.None) PlaygroundService.requireSide(request.master());
        return loop.command("playgroundClear", () -> loop.clear(request.master()));
    }

    @PostMapping("/{id}/immunity")
    public CompletableFuture<PlaygroundReply> immunity(@PathVariable String id,
            @AuthenticationPrincipal PrincipalDetails principal, @RequestBody Immunity request) {
        var loop = service.ownedLoop(id, principal.memberId);
        PlaygroundService.requireSide(request.master());
        return loop.command("playgroundImmunity", () -> loop.setImmune(request.master(), request.enabled()));
    }

    @GetMapping("/{id}/snapshot")
    public SnapshotResponseDto snapshot(@PathVariable String id, @AuthenticationPrincipal PrincipalDetails principal) {
        var loop = service.ownedLoop(id, principal.memberId);
        if (!loop.acceptsCommands()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.GONE, "Playground closed.");
        return loop.getLastSnapshot(principal.memberId);
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<PlaygroundReply> close(@PathVariable String id,
            @AuthenticationPrincipal PrincipalDetails principal) {
        var loop = service.ownedLoop(id, principal.memberId);
        return loop.command("playgroundClose", () -> {
            loop.end("CLOSED");
            return loop.reply(true, "Playground closed.");
        });
    }

    public record Cast(long magicId, Master master, Vector3 position) {}
    public record Clear(Master master) {}
    public record Immunity(Master master, boolean enabled) {}
}
