package com.example.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Пользовательское исключение для ошибок аутентификации.
 * Аннотация @ResponseStatus(HttpStatus.UNAUTHORIZED) автоматически
 * преобразует это исключение в HTTP-ответ со статусом 401 (Unauthorized),
 * когда оно выбрасывается из контроллера или сервиса.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED) // Это КЛЮЧЕВАЯ аннотация для статуса 401
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
