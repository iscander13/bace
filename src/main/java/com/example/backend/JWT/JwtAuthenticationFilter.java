package com.example.backend.JWT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException; // Добавлен импорт
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; // Добавлен импорт ArrayList
import lombok.extern.slf4j.Slf4j;

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
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter: No Bearer token found or Authorization header missing. Proceeding without authentication.");
            filterChain.doFilter(request, response);
            return;
        }

        jwtToken = authHeader.substring(7);
        log.info("JWT Filter: Extracted token: {}", jwtToken);

        try {
            userEmail = jwtService.extractUsername(jwtToken);
            List<String> roles = new ArrayList<>();

            Object rolesObject = jwtService.extractClaim(jwtToken, claims -> claims.get("roles"));
            if (rolesObject instanceof List) {
                ((List<?>) rolesObject).forEach(item -> {
                    if (item instanceof String) {
                        roles.add((String) item);
                    }
                });
            }
            
            log.info("JWT Filter: Extracted email: {} and roles: {} from token.", userEmail, roles);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // --- НОВАЯ ЛОГИКА: Обработка DEMO-пользователя ---
                if (roles.contains("ROLE_DEMO")) {
                    log.info("JWT Filter: Detected DEMO user: {}", userEmail);
                    // Создаем UserDetails для демо-пользователя без обращения к БД
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    UserDetails demoUserDetails = new org.springframework.security.core.userdetails.User(
                            userEmail, // "TEST"
                            "",        // Пароль не нужен для UserDetails демо-пользователя
                            authorities
                    );

                    if (jwtService.isTokenValid(jwtToken, demoUserDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                demoUserDetails,
                                null, // У демо-пользователя нет учетных данных (credentials)
                                demoUserDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.info("JWT Filter: Authenticated DEMO user: {} with roles: {}", userEmail, roles);
                    } else {
                        log.warn("JWT Filter: DEMO token is invalid or expired for user: {}", userEmail);
                    }
                } 
                // --- СУЩЕСТВУЮЩАЯ ЛОГИКА: Обработка ADMIN и обычных пользователей ---
                else if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SUPER_ADMIN")) { // Учитываем SUPER_ADMIN
                    // Если токен содержит роль ADMIN или SUPER_ADMIN, создаем временного пользователя
                    // (или загружаем из БД, если админы хранятся в БД)
                    // Для простоты, пока создаем временного пользователя, как вы делали для ADMIN
                    com.example.backend.entiity.User adminOrSuperAdminUser = com.example.backend.entiity.User.builder()
                                        .id(null) 
                                        .email(userEmail) 
                                        .passwordHash("") 
                                        .role(roles.contains("ROLE_SUPER_ADMIN") ? "SUPER_ADMIN" : "ADMIN") // Устанавливаем правильную роль
                                        .resetCode(null)
                                        .resetCodeExpiry(null)
                                        .build();

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            adminOrSuperAdminUser, null, adminOrSuperAdminUser.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("JWT Filter: Authenticated {} user: {}", adminOrSuperAdminUser.getRole(), userEmail);
                } else {
                    // Обычная логика для USER-токенов: ищем пользователя в БД
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
            log.warn("JWT Filter: Token expired.");
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

    // Метод loadRegularUser больше не нужен, его логика интегрирована выше
    // private void loadRegularUser(String userEmail, String jwt, HttpServletRequest request) {
    //    UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
    //    log.info("JWT Filter: Extracted email: {} and roles: {} from token.", userEmail, userDetails.getAuthorities());
    //
    //    if (jwtService.isTokenValid(jwt, userDetails)) {
    //        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
    //                userDetails,
    //                null,
    //                userDetails.getAuthorities()
    //        );
    //        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    //        SecurityContextHolder.getContext().setAuthentication(authToken);
    //        log.info("JWT Filter: Authenticated regular user: {} with roles: {}", userEmail, userDetails.getAuthorities());
    //    } else {
    //        log.warn("JWT Filter: Token is invalid or expired for user: {}", userEmail);
    //    }
    // }
}
