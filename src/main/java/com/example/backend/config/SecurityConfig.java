package com.example.backend.config;

import com.example.backend.JWT.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // Убедитесь, что это импортировано
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity // Включаем поддержку @PreAuthorize
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // Отключаем CSRF для REST API
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Настраиваем CORS
            .authorizeHttpRequests(auth -> auth
                // Разрешаем доступ к Swagger UI и API документации
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**",
                    // Разрешаем доступ к эндпоинтам аутентификации и восстановления пароля
                    "/api/v1/auth/**",
                    "/api/v1/recovery/**",
                    // Базовые пути, которые могут быть доступны без аутентификации (например, корневой URL)
                    "/",
                    "/error"
                ).permitAll() // Эти пути доступны всем
                
                // Только пользователи с ролью "ADMIN" или "SUPER_ADMIN" могут получить доступ к /api/v1/admin/**
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                
                // Все остальные API-эндпоинты, начинающиеся с /api/, требуют аутентификации
                // (но не требуют конкретной роли, если только это не /api/v1/admin/**)
                .requestMatchers("/api/**").authenticated()
                
                // Все остальные запросы (не /api) также требуют аутентификации
                .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Используем безсессионную аутентификацию (JWT)
            )
            .authenticationProvider(authenticationProvider()) // Указываем наш провайдер аутентификации
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class) // Добавляем JWT фильтр перед стандартным
            .formLogin().disable() // Отключаем стандартную форму входа
            .httpBasic().disable(); // Отключаем базовую HTTP-аутентификацию

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://agrofarm.kz",
                "https://user.agrofarm.kz",
                "https://www.user.agrofarm.kz",
                "https://www.agrofarm.kz"
        ));
        
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService); // Указываем наш UserDetailsService
        authProvider.setPasswordEncoder(passwordEncoder()); // Указываем наш PasswordEncoder
        return authProvider;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Используем BCrypt для хеширования паролей
    }
}