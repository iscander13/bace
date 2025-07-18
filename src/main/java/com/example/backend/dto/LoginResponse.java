package com.example.backend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data; // Добавлен импорт для @Builder
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder // Добавлена аннотация @Builder
public class LoginResponse {
    private String message;
    private String token;
    private List<String> roles;
}
