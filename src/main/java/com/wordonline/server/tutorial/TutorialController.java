package com.wordonline.server.tutorial;

import com.wordonline.server.auth.domain.PrincipalDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/tutorial/battles")
@PreAuthorize("isAuthenticated()")
public class TutorialController {
    private final TutorialService service;
    public TutorialController(TutorialService service) { this.service = service; }

    @GetMapping("/{id}/state")
    public TutorialBattle.State state(@PathVariable String id, @RequestParam(defaultValue = "0") int afterFrame,
            @AuthenticationPrincipal PrincipalDetails principal) {
        return service.owned(id, owner(principal)).state(afterFrame);
    }

    @PostMapping("/{id}/command")
    public Reply command(@PathVariable String id, @RequestBody TutorialBattle.Command request,
            @AuthenticationPrincipal PrincipalDetails principal) {
        service.owned(id, owner(principal)).command(request);
        return new Reply(true);
    }

    @DeleteMapping("/{id}")
    public Reply close(@PathVariable String id, @AuthenticationPrincipal PrincipalDetails principal) {
        service.close(id, owner(principal));
        return new Reply(true);
    }

    private static long owner(PrincipalDetails principal) {
        if (principal == null || principal.memberId == null || principal.memberId <= 0)
            throw TutorialBattle.error(HttpStatus.UNAUTHORIZED, "Authentication required");
        return principal.memberId;
    }
    public record Reply(boolean accepted) { }
}
