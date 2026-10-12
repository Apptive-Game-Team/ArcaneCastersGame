package com.wordonline.server.preview;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MagicPreviewControllerSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean MagicPreviewService service() { return mock(MagicPreviewService.class); }
        @Bean MagicPreviewController controller(MagicPreviewService service) { return new MagicPreviewController(service); }
    }

    @Test void serviceAuthorityIsRequiredEvenThoughServerRoutesPassTheHttpFilter() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(MagicPreviewController.class);
            var service = context.getBean(MagicPreviewService.class);
            when(service.catalog()).thenReturn(new PreviewCatalog("ready", "revision", List.of()));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "", List.of()));
            assertThatThrownBy(controller::catalog).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.recording("fire_shot", "revision"))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(service);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("lobby", "",
                    List.of(new SimpleGrantedAuthority("WORDONLINE_SERVER"))));
            assertThat(controller.catalog().getBody().status()).isEqualTo("ready");
            verify(service).catalog();
        } finally { SecurityContextHolder.clearContext(); }
    }
}
