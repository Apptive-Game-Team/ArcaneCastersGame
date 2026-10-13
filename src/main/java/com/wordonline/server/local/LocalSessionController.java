package com.wordonline.server.local;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * The desktop client hands this server the session the lobby issued. Authenticated like every other
 * request, which on a locally hosted server means the init token and nothing else.
 */
@RestController
@Profile("local")
@RequestMapping("/api/local")
@RequiredArgsConstructor
public class LocalSessionController {

    private final LocalSessionService localSessionService;

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public LocalSessionService.LocalSessionResponse createSession(@RequestBody LocalSessionRequest request) {
        return localSessionService.create(request);
    }
}
