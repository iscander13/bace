package com.example.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.UpdateUserRoleRequest;
import com.example.backend.entiity.User;
import com.example.backend.service.AdminService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ADMIN и SUPER_ADMIN могут просматривать пользователей
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // ADMIN и SUPER_ADMIN могут обновлять email
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @PutMapping("/users/{userId}/email")
    public ResponseEntity<String> updateUserEmail(@PathVariable Long userId, @RequestBody String newEmail) {
        return ResponseEntity.ok(adminService.updateUserEmail(userId, newEmail));
    }

    // ADMIN и SUPER_ADMIN могут сбрасывать пароль
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @PutMapping("/users/{userId}/password")
    public ResponseEntity<String> resetUserPassword(@PathVariable Long userId, @RequestBody String newPassword) {
        return ResponseEntity.ok(adminService.resetUserPassword(userId, newPassword));
    }

    // ADMIN и SUPER_ADMIN могут удалять пользователей
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.deleteUser(userId));
    }

    // Только SUPER_ADMIN может изменять роли пользователей
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<String> updateUserRole(@PathVariable Long userId, @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(adminService.updateUserRole(userId, request.getNewRole()));
    }
}