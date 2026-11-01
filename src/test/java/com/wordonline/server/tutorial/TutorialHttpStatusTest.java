package com.wordonline.server.tutorial;

import com.wordonline.server.auth.domain.PrincipalDetails;
import com.wordonline.server.exception.GlobalExceptionHandler;
import com.wordonline.server.service.LocalizationService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TutorialHttpStatusTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(ints = {400, 403, 404, 409, 410, 429, 503})
    void tutorialRejectionsKeepTheirStatusAlongsideTheGlobalHandler(int code) throws Exception {
        var service = mock(TutorialService.class);
        var exception = new ResponseStatusException(HttpStatus.valueOf(code), "Tutorial rejected");
        when(service.owned(anyString(), anyLong())).thenThrow(exception);
        when(service.create(anyLong(), anyString(), anyString())).thenThrow(exception);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new PrincipalDetails(7L, List.of()), "", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new TutorialController(service), new TutorialServerController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(mock(LocalizationService.class)), new TutorialApiExceptionHandler())
                .build();
        mvc.perform(get("/api/tutorial/battles/tutorial-test/state"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").value("Tutorial rejected"));
        mvc.perform(post("/api/server/tutorial/battles").contentType("application/json")
                        .content("{\"ownerId\":7,\"faction\":\"HELL\",\"requestId\":\"test\"}"))
                .andExpect(status().is(code));
    }
}
