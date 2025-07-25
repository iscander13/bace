package com.example.backend.JWT;

import java.io.IOException;
import java.util.List; // Используем List вместо ArrayList, так как ArrayList не нужен напрямую

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// НОВОЕ: Импорты для специфических исключений JWT
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        log.info("JWT Filter: Request URL: {}", request.getRequestURI());

        final String authHeader = request.getHeader("Authorization");
        final String jwtToken;
        String userEmail = null;

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
                if (jwtService.isDemoToken(jwtToken)) {
                    log.info("JWT Filter: Authenticating DEMO user: {}", userEmail);
                    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_DEMO"));

                    User demoUserDetails = new User(
                        0L, // Фиктивный ID для демо
                        userEmail, 
                        "", 
                        "DEMO", 
                        null, null
                    );

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        demoUserDetails, 
                        null,
                        authorities 
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("JWT Filter: DEMO user authenticated: {}", userEmail);

                } else {
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

        } catch (ExpiredJwtException e) {
            log.warn("JWT Filter: Token expired for user {}: {}", userEmail != null ? userEmail : "unknown", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token expired\"}");
            return;
        } catch (SignatureException e) { // НОВОЕ: Явный перехват SignatureException
            log.warn("JWT Filter: Invalid JWT signature for user {}: {}", userEmail != null ? userEmail : "unknown", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid JWT signature\"}"); // Более точное сообщение
            return;
        } catch (MalformedJwtException e) { // НОВОЕ: Явный перехват MalformedJwtException
            log.warn("JWT Filter: Malformed JWT for user {}: {}", userEmail != null ? userEmail : "unknown", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Malformed JWT\"}"); // Более точное сообщение
            return;
        } catch (Exception e) { // Общий перехват для других неожиданных ошибок
            log.error("JWT Filter: Error processing token for user {}: {}", userEmail != null ? userEmail : "unknown", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }
    
        filterChain.doFilter(request, response);
    }
}
