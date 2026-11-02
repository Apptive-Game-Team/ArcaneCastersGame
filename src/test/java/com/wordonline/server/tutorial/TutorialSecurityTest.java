package com.wordonline.server.tutorial;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TutorialSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean TutorialService service() { return mock(TutorialService.class); }
        @Bean TutorialServerController controller(TutorialService service) { return new TutorialServerController(service); }
    }

    @Test void ordinaryOrGuestUserCannotChooseAnOwnerOrFactionOnTheServerRoute() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(TutorialServerController.class);
            var service = context.getBean(TutorialService.class);
            var request = new TutorialServerController.Create(7, "HELL", "request");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("guest", "", List.of()));
            assertThatThrownBy(() -> controller.create(request)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(service);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("lobby", "",
                    List.of(new SimpleGrantedAuthority("WORDONLINE_SERVER"))));
            controller.create(request);
            verify(service).create(7, "HELL", "request");
        } finally { SecurityContextHolder.clearContext(); }
    }
}
