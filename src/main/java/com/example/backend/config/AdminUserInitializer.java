package com.example.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.backend.entiity.User;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Проверяем, существует ли пользователь-администратор
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            User admin = User.builder()
                    .email("admin@example.com")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);
            System.out.println("Создан пользователь-администратор: admin@example.com");
        } else {
            System.out.println("Пользователь-администратор уже существует.");
        }

        // Проверяем, существует ли пользователь-супер-администратор
        if (userRepository.findByEmail("superadmin@example.com").isEmpty()) {
            User superAdmin = User.builder()
                    .email("superadmin@example.com") // Email супер-администратора
                    .passwordHash(passwordEncoder.encode("superadminpass")) // Установите надежный пароль
                    .role("SUPER_ADMIN") // Роль супер-администратора
                    .build();
            userRepository.save(superAdmin);
            System.out.println("Создан пользователь-супер-администратор: superadmin@example.com");
        } else {
            System.out.println("Пользователь-супер-администратор уже существует.");
        }
    }
}