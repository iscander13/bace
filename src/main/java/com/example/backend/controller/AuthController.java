package com.example.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.AdminLoginRequest;
import com.example.backend.dto.AdminLoginResponse;
import com.example.backend.dto.DemoLoginRequest; // Существующий DTO для обычного входа
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.RegisterRequest; // НОВЫЙ ИМПОРТ для демо-входа
import com.example.backend.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login") // Эндпоинт для обычных пользователей (использует email)
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // НОВЫЙ ЭНДПОИНТ: Вход для демо-доступа
    @PostMapping("/demo/login") // Новый путь для демо-входа
    public ResponseEntity<LoginResponse> demoLogin(@RequestBody DemoLoginRequest request) { // Используем DemoLoginRequest
        return ResponseEntity.ok(authService.demoLogin(request)); // Делегируем в новый метод сервиса
    }

    @PostMapping("/admin/login")
    public ResponseEntity<AdminLoginResponse> adminLogin(@RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(authService.adminLogin(request));
    }
}
