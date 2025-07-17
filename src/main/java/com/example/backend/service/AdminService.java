package com.example.backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.backend.entiity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PolygonAreaRepository polygonAreaRepository;
    private final ChatMessageRepository chatMessageRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public String updateUserEmail(Long userId, String newEmail) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("Пользователь с ID " + userId + " не найден.");
        }

        User user = userOptional.get();
        if (userRepository.findByEmail(newEmail).isPresent() && !user.getEmail().equals(newEmail)) {
            throw new RuntimeException("Пользователь с email " + newEmail + " уже существует.");
        }

        user.setEmail(newEmail);
        userRepository.save(user);
        return "Email пользователя " + userId + " успешно обновлен на " + newEmail;
    }

    @Transactional
    public String resetUserPassword(Long userId, String newPassword) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("Пользователь с ID " + userId + " не найден.");
        }

        User user = userOptional.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return "Пароль пользователя " + userId + " успешно обновлен.";
    }

    @Transactional
    public String deleteUser(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("Пользователь с ID " + userId + " не найден.");
        }

        chatMessageRepository.deleteByUser_Id(userId);
        polygonAreaRepository.deleteByUser_Id(userId);
        userRepository.deleteById(userId);
        return "Пользователь с ID " + userId + " и все его данные успешно удалены.";
    }

    @Transactional
    public String updateUserRole(Long userId, String newRole) {
        // Получаем информацию об аутентифицированном пользователе
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Предполагаем, что у пользователя одна роль. Если может быть несколько, нужно адаптировать.
        String currentUsersRole = authentication.getAuthorities().iterator().next().getAuthority();

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("Пользователь с ID " + userId + " не найден.");
        }

        User userToUpdate = userOptional.get();
        String normalizedNewRole = newRole.toUpperCase();

        // Валидация новой роли
        if (!normalizedNewRole.equals("USER") && !normalizedNewRole.equals("ADMIN") && !normalizedNewRole.equals("SUPER_ADMIN")) {
            throw new IllegalArgumentException("Недопустимая роль: " + newRole + ". Допустимые роли: USER, ADMIN, SUPER_ADMIN.");
        }
        
        // --- Применение иерархии ролей ---
        // Если текущий пользователь - ADMIN, он имеет ограничения
        if (currentUsersRole.equals("ROLE_ADMIN")) { // Spring Security добавляет префикс "ROLE_"
            // ADMIN не может назначать роли ADMIN или SUPER_ADMIN
            if (normalizedNewRole.equals("ADMIN") || normalizedNewRole.equals("SUPER_ADMIN")) {
                throw new SecurityException("Администратор не может присваивать роль " + normalizedNewRole + ".");
            }
            // ADMIN не может изменять роль SUPER_ADMIN пользователя
            if (userToUpdate.getRole().equals("SUPER_ADMIN")) {
                throw new SecurityException("Администратор не может изменять роль супер-администратора.");
            }
        }
        // SUPER_ADMIN может изменять любые роли.

        userToUpdate.setRole(normalizedNewRole);
        userRepository.save(userToUpdate);
        return "Роль пользователя " + userId + " успешно изменена на " + normalizedNewRole + ".";
    }
}