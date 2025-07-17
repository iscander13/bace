// src/main/java/com/example/backend/dto/LoginResponse.java
package com.example.backend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor; // Важно: импортируйте List

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String message;
    private String token;
    private List<String> roles; // <-- ДОБАВЬТЕ ЭТУ СТРОКУ
}