package com.example.backend.entiity; // Ваш текущий пакет

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // автоинкремент Long id
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash; // Используем ваше текущее название поля

    @Column(nullable = false) // <--- ДОБАВЛЕНО: Поле для роли
    private String role; // Например, "USER", "ADMIN"

    // Для восстановления пароля
    private String resetCode;
    private LocalDateTime resetCodeExpiry;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // <--- ИЗМЕНЕНО: Возвращаем роль из поля 'role' с префиксом "ROLE_"
        return List.of(new SimpleGrantedAuthority("ROLE_" + role)); 
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email; // используем email как username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
