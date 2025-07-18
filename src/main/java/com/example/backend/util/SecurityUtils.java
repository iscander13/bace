package com.example.backend.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    /**
     * Проверяет, является ли текущий аутентифицированный пользователь демо-пользователем.
     * Демо-пользователь идентифицируется по роли "ROLE_DEMO".
     * @return true, если пользователь является демо-пользователем, иначе false.
     */
    public static boolean isDemoUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false; // Пользователь не аутентифицирован
        }
        // Проверяем, есть ли у пользователя роль "ROLE_DEMO"
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_DEMO".equals(role));
    }
}
