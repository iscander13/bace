package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRoleRequest {
    private String newRole; // Новая роль, например "USER" или "ADMIN"
    // Здесь НИЧЕГО БОЛЬШЕ НЕ ДОЛЖНО БЫТЬ. Lombok сделает остальное.
}