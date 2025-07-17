package com.example.backend.service;

import java.util.Collections;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager; // Убедитесь, что Optional импортирован
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.backend.JWT.JwtService;
import com.example.backend.dto.AdminLoginRequest;
import com.example.backend.dto.AdminLoginResponse;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.RegisterRequest; // Убедитесь, что это ваш класс пользователя
import com.example.backend.entiity.User;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor; // Импорт для создания списка из одной роли

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public String register(RegisterRequest request) {
        try {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                return "User with this email already exists";
            }

            User user = User.builder()
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .role("USER") // Убедитесь, что у вас есть это поле 'role' в сущности User
                    .resetCode(null)
                    .resetCodeExpiry(null)
                    .build();

            userRepository.save(user);
            return "User registered successfully";
        } catch (Exception e) {
            e.printStackTrace();
            return "Registration failed: " + e.getMessage();
        }
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден после аутентификации."));

        String token = jwtService.generateToken(user); 
        
        // Получаем роль пользователя из объекта User
        List<String> userRoles = Collections.singletonList(user.getRole()); // Предполагается, что getRole() возвращает String

        // Возвращаем LoginResponse с сообщением, токеном и РОЛЬЮ
        return new LoginResponse("Login successful", token, userRoles); // <-- ОБНОВЛЕННАЯ СТРОКА
    }

    public AdminLoginResponse adminLogin(AdminLoginRequest request) {
        User adminUser = userRepository.findByEmail(request.getUsername()) 
                .orElseThrow(() -> new RuntimeException("Неверные учетные данные администратора."));

        if (!passwordEncoder.matches(request.getPassword(), adminUser.getPasswordHash())) {
            throw new RuntimeException("Неверные учетные данные администратора.");
        }

        // ИЗМЕНЕНИЕ ЗДЕСЬ: Разрешаем вход для ADMIN и SUPER_ADMIN
        if (!"ADMIN".equals(adminUser.getRole()) && !"SUPER_ADMIN".equals(adminUser.getRole())) {
            throw new RuntimeException("У вас нет прав администратора или супер-администратора.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String adminJwt = jwtService.generateToken(adminUser);
        
        return AdminLoginResponse.builder()
                .adminToken(adminJwt)
                .message("Вход администратора успешен!")
                .build();
    }
}
