package com.wordonline.server.tutorial;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 전역 예외 처리기가 튜토리얼의 명시적인 HTTP 상태를 500으로 바꾸지 않게 한다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {TutorialController.class, TutorialServerController.class})
public class TutorialApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> reject(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Tutorial request rejected" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }
}
