package com.example.backend.entiity;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public enum Role {
    USER(Collections.emptySet()),
    ADMIN(
            Set.of(
                    Permission.ADMIN_READ,
                    Permission.ADMIN_UPDATE,
                    Permission.ADMIN_DELETE,
                    Permission.ADMIN_CREATE
            )
    ),
    SUPER_ADMIN(
            Set.of(
                    Permission.SUPER_ADMIN_READ,
                    Permission.SUPER_ADMIN_UPDATE,
                    Permission.SUPER_ADMIN_DELETE,
                    Permission.SUPER_ADMIN_CREATE,
                    Permission.ADMIN_READ,
                    Permission.ADMIN_UPDATE,
                    Permission.ADMIN_DELETE,
                    Permission.ADMIN_CREATE
            )
    ),
    // НОВАЯ РОЛЬ: Демо-пользователь
    DEMO(
            Set.of(
                    // Определите здесь только те разрешения, которые нужны демо-пользователю
                    // Например, только на чтение фиктивных данных
                    Permission.DEMO_READ // Пример разрешения для демо-пользователя
            )
    );

    private final Set<Permission> permissions;

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public List<SimpleGrantedAuthority> getAuthorities() {
        var authorities = getPermissions()
                .stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toList());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return authorities;
    }
}
