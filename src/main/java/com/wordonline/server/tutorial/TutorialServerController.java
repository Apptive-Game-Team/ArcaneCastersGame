package com.wordonline.server.tutorial;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/server/tutorial/battles")
@PreAuthorize("hasAuthority('WORDONLINE_SERVER')")
public class TutorialServerController {
    private final TutorialService service;
    public TutorialServerController(TutorialService service) { this.service = service; }
    @PostMapping public TutorialService.Ready create(@RequestBody Create request) {
        return service.create(request.ownerId(), request.faction(), request.requestId());
    }
    public record Create(long ownerId, String faction, String requestId) { }
}
