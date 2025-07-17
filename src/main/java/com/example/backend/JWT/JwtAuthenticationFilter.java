package com.example.backend.JWT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.backend.entiity.User;
import com.example.backend.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest; // Ваш кастомный User entity
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
                                        
        final String authHeader = request.getHeader("Authorization");
        log.info("JWT Filter: Request URL: {}", request.getRequestURI());
                                        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter: No Bearer token found or Authorization header missing. Proceeding without authentication.");
            filterChain.doFilter(request, response);
            return;
        }
    
        final String jwtToken = authHeader.substring(7);
        log.info("JWT Filter: Extracted token: {}", jwtToken);
        
        try {
            final String userEmail = jwtService.extractUsername(jwtToken); // <--- ИЗМЕНЕНО: Объявлено final здесь
            final List<String> roles = new ArrayList<>(); // <--- ИЗМЕНЕНО: Объявлено final здесь

            Object rolesObject = jwtService.extractClaim(jwtToken, claims -> claims.get("roles"));
            if (rolesObject instanceof List) {
                ((List<?>) rolesObject).forEach(item -> {
                    if (item instanceof String) {
                        roles.add((String) item);
                    }
                });
            }
            
            log.info("JWT Filter: Extracted email: {} and roles: {} from token.", userEmail, roles);
        
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                if (roles.contains("ROLE_ADMIN")) { // Проверяем, содержит ли токен роль ADMIN
                    // Если токен содержит роль ADMIN, создаем временного пользователя ADMIN
                    User adminUser = User.builder()
                                        .id(null) // Используем null для автоинкрементного ID
                                        .email(userEmail) // userEmail теперь final
                                        .passwordHash("") // Пароль не нужен для временного объекта
                                        .role("ADMIN")
                                        .resetCode(null)
                                        .resetCodeExpiry(null)
                                        .build();

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            adminUser, null, adminUser.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("JWT Filter: Authenticated ADMIN user: {}", userEmail);
                } else {
                    // Обычная логика для USER-токенов: ищем пользователя в БД
                    userRepository.findByEmail(userEmail).ifPresentOrElse(user -> { // userEmail теперь final
                        if (jwtService.isTokenValid(jwtToken, user)) { 
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    user, null, user.getAuthorities());
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            log.info("JWT Filter: Authenticated user: {}", user.getEmail());
                        } else {
                            log.warn("JWT Filter: Token is not valid for user: {}", user.getEmail());
                        }
                    }, () -> {
                        log.warn("JWT Filter: No user found with email: {}", userEmail); // userEmail теперь final
                    });
                }
            } else {
                log.info("JWT Filter: User already authenticated (from previous filter or context).");
            }

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT Filter: Token expired.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token expired\"}");
            return;
        } catch (Exception e) {
            log.error("JWT Filter: Error extracting claims from token", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }
    
        filterChain.doFilter(request, response);
    }
}
