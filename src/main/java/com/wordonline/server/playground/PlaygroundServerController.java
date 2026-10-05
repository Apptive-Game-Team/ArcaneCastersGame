package com.wordonline.server.playground;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "playground.enabled", havingValue = "true")
@PreAuthorize("hasAuthority('WORDONLINE_SERVER')")
@RequestMapping("/api/server/playgrounds")
public class PlaygroundServerController {
    private final PlaygroundService service;

    @PostMapping
    public PlaygroundInfo create(@RequestBody Create request) {
        return service.create(request.ownerId());
    }

    public record Create(long ownerId) {}
}
