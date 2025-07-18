package com.example.backend.JWT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.backend.entiity.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest; // Добавлен импорт
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Добавлен импорт ArrayList

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService; // Используем UserDetailsService для обычных пользователей

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        log.info("JWT Filter: Request URL: {}", request.getRequestURI());

        final String authHeader = request.getHeader("Authorization");
        final String jwtToken; // Переименовано для ясности
        String userEmail = null; // Инициализируем null

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter: No Bearer token found or Authorization header missing. Proceeding without authentication.");
            filterChain.doFilter(request, response);
            return;
        }

        jwtToken = authHeader.substring(7);
        log.info("JWT Filter: Extracted token: {}", jwtToken);

        try {
            userEmail = jwtService.extractUsername(jwtToken);
            
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // ✨ НОВОЕ: Проверка на демо-токен
                if (jwtService.isDemoToken(jwtToken)) {
                    log.info("JWT Filter: Authenticating DEMO user: {}", userEmail);
                    // Для демо-пользователя создаем SimpleGrantedAuthority напрямую
                    // Используем "ROLE_DEMO" как авторитет
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_DEMO"));

                    // Создаем фиктивный UserDetails для демо-пользователя
                    // Это важно, чтобы Spring Security мог работать с ним
                    // Используем ваш класс User для создания UserDetails, так как он его реализует
                    User demoUserDetails = new User(
                        0L, // Фиктивный ID для демо
                        userEmail, 
                        "", // Пароль не нужен для аутентификации по токену
                        "DEMO", // Роль
                        null, null // Для сброса пароля
                    );

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        demoUserDetails, // Используем фиктивный UserDetails
                        null,
                        authorities // Используем явно созданные авторитеты
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("JWT Filter: DEMO user authenticated: {}", userEmail);

                } else {
                    // Существующая логика для обычных пользователей
                    // Используем userDetailsService для загрузки UserDetails
                    UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                    
                    if (jwtService.isTokenValid(jwtToken, userDetails)) { 
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.info("JWT Filter: Authenticated regular user: {}", userDetails.getUsername());
                    } else {
                        log.warn("JWT Filter: Token is not valid for user: {}", userEmail);
                    }
                }
            } else {
                log.info("JWT Filter: User already authenticated (from previous filter or context).");
            }

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT Filter: Token expired for user {}", userEmail != null ? userEmail : "unknown");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token expired\"}");
            return;
        } catch (Exception e) {
            log.error("JWT Filter: Error processing token", e); // Более общее сообщение
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }
    
        filterChain.doFilter(request, response);
    }
}
