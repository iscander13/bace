package com.example.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolygonService {

    private final PolygonAreaRepository polygonAreaRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;

    // Вспомогательный метод для получения текущего аутентифицированного пользователя
    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("PolygonService: Пользователь не аутентифицирован.");
            throw new IllegalStateException("Пользователь не аутентифицирован.");
        }

        Object principal = authentication.getPrincipal();
        String userEmail = null;
        List<String> userRoles = new ArrayList<>();

        // Извлекаем email и роли из Principal
        if (principal instanceof User castedUser) {
            userEmail = castedUser.getEmail();
            userRoles = castedUser.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toList());
        } else if (principal instanceof org.springframework.security.core.userdetails.User springUser) {
            userEmail = springUser.getUsername();
            userRoles = springUser.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toList());
        } else {
            log.error("PolygonService: Неизвестный тип принципала: {}", principal.getClass().getName());
            throw new IllegalStateException("Неизвестный тип принципала.");
        }

        // --- КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка DEMO-пользователя без обращения к базе данных ---
        if (userRoles.contains("ROLE_DEMO")) {
            log.info("PolygonService: getCurrentAuthenticatedUser - Обнаружен DEMO-пользователь. Возвращаем фиктивного пользователя.");
            // Возвращаем фиктивный объект User для роли DEMO
            return User.builder()
                    .id(0L) // Фиктивный ID для демо-пользователя (0 или другое уникальное значение)
                    .email(userEmail) // Используем email из токена ("TEST")
                    .passwordHash("") // Хэш пароля не имеет значения для фиктивного пользователя
                    .role("DEMO") // Назначаем роль DEMO
                    .build();
        }

        // Для всех остальных ролей (USER, ADMIN, SUPER_ADMIN) - ищем в базе данных
        log.info("PolygonService: getCurrentAuthenticatedUser - Ищем пользователя {} в базе данных.", userEmail);
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден в базе данных."));
    }

    // Создание нового полигона
    @Transactional
    public PolygonArea createPolygon(PolygonRequestDto polygonRequestDto, Long targetUserId) {
        User currentUser = getCurrentAuthenticatedUser();
        
        // --- ЛОГИКА ДЛЯ DEMO-ПОЛЬЗОВАТЕЛЯ: Имитация создания без сохранения в БД ---
        if ("DEMO".equals(currentUser.getRole())) {
            log.info("PolygonService: DEMO-пользователь (ID: {}) попытался создать полигон. Данные не сохранены.", currentUser.getId());
            return PolygonArea.builder()
                    .id(UUID.randomUUID()) // Генерируем случайный UUID для имитации
                    .name(polygonRequestDto.getName())
                    .comment(polygonRequestDto.getComment())
                    .color(polygonRequestDto.getColor())
                    .crop(polygonRequestDto.getCrop())
                    .geoJson(polygonRequestDto.getGeoJson())
                    .user(currentUser) // Присваиваем фиктивного демо-пользователя
                    .build();
        }
        // --- КОНЕЦ ЛОГИКИ DEMO ---

        log.info("Текущий аутентифицированный пользователь: ID={}, Роль={}, Email={}", currentUser.getId(), currentUser.getRole(), currentUser.getEmail());

        User ownerUser;
        if (targetUserId != null) {
            if (!"ADMIN".equals(currentUser.getRole()) && !"SUPER_ADMIN".equals(currentUser.getRole())) {
                log.warn("ПОЛЬЗОВАТЕЛЬ (ID: {}) попытался создать полигон для targetUserId: {} без роли ADMIN/SUPER_ADMIN.", currentUser.getId(), targetUserId);
                throw new SecurityException("У вас нет прав для создания полигонов для других пользователей.");
            }
            ownerUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new RuntimeException("Целевой пользователь не найден."));
            log.info("ADMIN/SUPER_ADMIN (ID: {}) создает полигон для целевого пользователя (ID: {})", currentUser.getId(), targetUserId);
        } else {
            ownerUser = currentUser;
            log.info("Пользователь (ID: {}) создает полигон для себя.", currentUser.getId());
        }

        PolygonArea polygonArea = PolygonArea.builder()
                .id(polygonRequestDto.getId() != null ? polygonRequestDto.getId() : UUID.randomUUID())
                .name(polygonRequestDto.getName())
                .comment(polygonRequestDto.getComment())
                .color(polygonRequestDto.getColor())
                .crop(polygonRequestDto.getCrop())
                .geoJson(polygonRequestDto.getGeoJson())
                .user(ownerUser)
                .build();
        return polygonAreaRepository.save(polygonArea);
    }

    // Получение всех полигонов текущего пользователя или целевого пользователя (для ADMIN/SUPER_ADMIN)
    public List<PolygonArea> getPolygonsForCurrentUser(Long targetUserId) {
        User currentUser = getCurrentAuthenticatedUser();
        
        // --- ЛОГИКА ДЛЯ DEMO-ПОЛЬЗОВАТЕЛЯ: Возвращаем ПУСТОЙ список полигонов ---
        if ("DEMO".equals(currentUser.getRole())) {
            log.info("PolygonService: DEMO-пользователь (ID: {}) запрашивает полигоны. Возвращаем пустой список (нет реальных полигонов для демо).", currentUser.getId());
            // Для демо-пользователя возвращаем пустой список, так как у него нет реальных полигонов в БД.
            // Если бы мы хотели "фиктивные" полигоны, которые не сохраняются, но отображаются,
            // мы бы их генерировали здесь. Но вы просили не брать чужие, и не сохранять свои.
            return List.of(); // <-- КРИТИЧЕСКОЕ ИЗМЕНЕНИЕ ЗДЕСЬ: Возвращаем пустой список
        }
        // --- КОНЕЦ ЛОГИКИ DEMO ---

        String currentUserRole = currentUser.getRole();
        log.info("PolygonService: getPolygonsForCurrentUser вызван пользователем ID: {} с ролью: {}", currentUser.getId(), currentUserRole);

        if ("USER".equals(currentUserRole)) {
            if (targetUserId != null && !currentUser.getId().equals(targetUserId)) {
                log.warn("ПОЛЬЗОВАТЕЛЬ (ID: {}) попытался просмотреть полигоны целевого пользователя (ID: {}). Доступ запрещен.", currentUser.getId(), targetUserId);
                throw new SecurityException("У вас нет разрешения на просмотр полигонов других пользователей.");
            }
            log.info("ПОЛЬЗОВАТЕЛЬ (ID: {}) получает свои полигоны.", currentUser.getId());
            return polygonAreaRepository.findByUser_Id(currentUser.getId());
        } else if ("ADMIN".equals(currentUserRole) || "SUPER_ADMIN".equals(currentUserRole)) {
            if (targetUserId != null) {
                User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new RuntimeException("Целевой пользователь не найден."));
                
                if ("ADMIN".equals(currentUserRole) && ("ADMIN".equals(targetUser.getRole()) || "SUPER_ADMIN".equals(targetUser.getRole()))) {
                    if (currentUser.getId().equals(targetUserId)) {
                        log.info("ADMIN (ID: {}) получает свои полигоны.", currentUser.getId());
                        return polygonAreaRepository.findByUser_Id(currentUser.getId());
                    } else {
                        log.warn("ADMIN (ID: {}) попытался просмотреть полигоны пользователя (ID: {}) с ролью {}. Доступ запрещен.", currentUser.getId(), targetUserId, targetUser.getRole());
                        throw new SecurityException("У вас нет разрешения на просмотр полигонов пользователей с ролью " + targetUser.getRole() + ".");
                    }
                }
                log.info("{} (ID: {}) получает полигоны для целевого пользователя (ID: {}) с ролью {}.", currentUserRole, currentUser.getId(), targetUserId, targetUser.getRole());
                return polygonAreaRepository.findByUser_Id(targetUserId);
            } else {
                log.info("{} (ID: {}) получает свои полигоны (targetUserId не указан).", currentUserRole, currentUser.getId());
                return polygonAreaRepository.findByUser_Id(currentUser.getId());
            }
        } else {
            log.warn("Неизвестная роль пользователя {} (ID: {}) попыталась получить полигоны. Доступ запрещен.", currentUserRole, currentUser.getId());
            throw new SecurityException("Недостаточно прав для выполнения операции.");
        }
    }


    // Обновление существующего полигона
    @Transactional
    public PolygonArea updatePolygon(UUID polygonId, PolygonRequestDto polygonRequestDto) {
        User currentUser = getCurrentAuthenticatedUser();

        // --- ЛОГИКА ДЛЯ DEMO-ПОЛЬЗОВАТЕЛЯ: Имитация обновления ---
        if ("DEMO".equals(currentUser.getRole())) {
            log.info("PolygonService: DEMO-пользователь (ID: {}) попытался обновить полигон. Данные не сохранены.", currentUser.getId());
            return PolygonArea.builder()
                    .id(polygonId)
                    .name(polygonRequestDto.getName())
                    .comment(polygonRequestDto.getComment())
                    .color(polygonRequestDto.getColor())
                    .crop(polygonRequestDto.getCrop())
                    .geoJson(polygonRequestDto.getGeoJson())
                    .user(currentUser)
                    .build();
        }
        // --- КОНЕЦ ЛОГИКИ DEMO ---

        String currentUserRole = currentUser.getRole();
        log.info("Попытка обновить полигон ID: {} пользователем ID: {} с ролью: {}", polygonId, currentUser.getId(), currentUserRole);

        PolygonArea existingPolygon = polygonAreaRepository.findById(polygonId)
                .orElseThrow(() -> new RuntimeException("Полигон не найден."));

        // Проверка прав доступа
        if ("SUPER_ADMIN".equals(currentUserRole)) {
            log.info("SUPER_ADMIN (ID: {}) обновляет полигон ID: {} владельца ПОЛЬЗОВАТЕЛЯ (ID: {})", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
        } else if ("ADMIN".equals(currentUserRole)) {
            if (existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.info("ADMIN (ID: {}) обновляет свой полигон ID: {}", currentUser.getId(), polygonId);
            } else if ("USER".equals(existingPolygon.getUser().getRole())) {
                log.info("ADMIN (ID: {}) обновляет полигон ПОЛЬЗОВАТЕЛЯ (ID: {}) ID: {}", currentUser.getId(), existingPolygon.getUser().getId(), polygonId);
            } else {
                log.warn("ADMIN (ID: {}) попытался обновить полигон ID: {} владельца с ролью {}. Доступ запрещен.", currentUser.getId(), polygonId, existingPolygon.getUser().getRole());
                throw new SecurityException("Администратор не может редактировать полигоны пользователей с ролью " + existingPolygon.getUser().getRole() + ".");
            }
        } else if ("USER".equals(currentUserRole)) {
            if (!existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.warn("ПОЛЬЗОВАТЕЛЬ (ID: {}) попытался обновить полигон ID: {} владельца ID: {}. Доступ запрещен.", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
                throw new SecurityException("У вас нет разрешения на редактирование этого полигона.");
            }
            log.info("ПОЛЬЗОВАТЕЛЬ (ID: {}) обновляет свой полигон ID: {}", currentUser.getId(), polygonId);
        } else {
            log.warn("Неизвестная роль пользователя {} (ID: {}) попыталась обновить полигон ID: {}. Доступ запрещен.", currentUserRole, currentUser.getId(), polygonId);
            throw new SecurityException("Недостаточно прав для выполнения операции.");
        }

        existingPolygon.setName(polygonRequestDto.getName());
        existingPolygon.setComment(polygonRequestDto.getComment());
        existingPolygon.setColor(polygonRequestDto.getColor());
        existingPolygon.setCrop(polygonRequestDto.getCrop());
        existingPolygon.setGeoJson(polygonRequestDto.getGeoJson());
        return polygonAreaRepository.save(existingPolygon);
    }

    // Удаление полигона
    @Transactional
    public void deletePolygon(UUID polygonId) {
        User currentUser = getCurrentAuthenticatedUser();

        // --- ЛОГИКА ДЛЯ DEMO-ПОЛЬЗОВАТЕЛЯ: Имитация удаления ---
        if ("DEMO".equals(currentUser.getRole())) {
            log.info("PolygonService: DEMO-пользователь (ID: {}) попытался удалить полигон. Данные не удалены.", currentUser.getId());
            return; // Имитация успешного удаления
        }
        // --- КОНЕЦ ЛОГИКИ DEMO ---

        String currentUserRole = currentUser.getRole();
        log.info("Попытка удалить полигон ID: {} пользователем ID: {} с ролью: {}", polygonId, currentUser.getId(), currentUserRole);

        PolygonArea existingPolygon = polygonAreaRepository.findById(polygonId)
                .orElseThrow(() -> new RuntimeException("Полигон не найден."));

        // Проверка прав доступа
        if ("SUPER_ADMIN".equals(currentUserRole)) {
            log.info("SUPER_ADMIN (ID: {}) удаляет полигон ID: {} владельца ПОЛЬЗОВАТЕЛЯ (ID: {})", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
        } else if ("ADMIN".equals(currentUserRole)) {
            if (existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.info("ADMIN (ID: {}) удаляет свой полигон ID: {}", currentUser.getId(), polygonId);
            } else if ("USER".equals(existingPolygon.getUser().getRole())) {
                log.info("ADMIN (ID: {}) удаляет полигон ПОЛЬЗОВАТЕЛЯ (ID: {}) ID: {}", currentUser.getId(), existingPolygon.getUser().getId(), polygonId);
            } else {
                log.warn("ADMIN (ID: {}) попытался удалить полигон ID: {} владельца с ролью {}. Доступ запрещен.", currentUser.getId(), polygonId, existingPolygon.getUser().getRole());
                throw new SecurityException("Администратор не может удалять полигоны пользователей с ролью " + existingPolygon.getUser().getRole() + ".");
            }
        } else if ("USER".equals(currentUser.getRole())) { // <-- ИСПРАВЛЕНИЕ ЗДЕСЬ: Убрана лишняя закрывающая скобка
            if (!existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.warn("ПОЛЬЗОВАТЕЛЬ (ID: {}) попытался удалить полигон ID: {} владельца ID: {}. Доступ запрещен.", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
                throw new SecurityException("У вас нет разрешения на удаление этого полигона.");
            }
            log.info("ПОЛЬЗОВАТЕЛЬ (ID: {}) удаляет свой полигон ID: {}", currentUser.getId(), polygonId);
        } else {
            log.warn("Неизвестная роль пользователя {} (ID: {}) попыталась удалить полигон ID: {}. Доступ запрещен.", currentUserRole, currentUser.getId(), polygonId);
            throw new SecurityException("Недостаточно прав для выполнения операции.");
        }

        chatMessageRepository.deleteByPolygonArea_IdAndUser_Id(polygonId, existingPolygon.getUser().getId());
        log.info("Удалены сообщения чата для полигона ID: {}", polygonId);
        
        polygonAreaRepository.delete(existingPolygon);
        log.info("Удален полигон ID: {}", polygonId);
    }

    // Очистка всех полигонов текущего пользователя
    @Transactional
    public void deleteAllPolygonsForUser(User principalUser) { // Изменен тип возврата на void, убран @AuthenticationPrincipal
        User currentUser = getCurrentAuthenticatedUser();

        // --- ЛОГИКА ДЛЯ DEMO-ПОЛЬЗОВАТЕЛЯ: Имитация очистки ---
        if ("DEMO".equals(currentUser.getRole())) {
            log.info("PolygonService: DEMO-пользователь (ID: {}) попытался очистить все полигоны. Данные не удалены.", currentUser.getId());
            return; // Имитация успешной очистки
        }
        // --- КОНЕЦ ЛОГИКИ DEMO ---

        log.info("Очистка всех полигонов для пользователя ID: {}", currentUser.getId());
        
        chatMessageRepository.deleteByUser_Id(currentUser.getId());
        log.info("Удалены все сообщения чата для пользователя ID: {}", currentUser.getId());

        polygonAreaRepository.deleteByUser_Id(currentUser.getId());
        log.info("Удалены все полигоны для пользователя ID: {}", currentUser.getId());
    }

    /**
     * Вспомогательный метод для создания фиктивных данных полигонов для демо-режима.
     * Этот метод больше не используется для чтения, только для имитации возвращаемых объектов.
     * @return Список фиктивных PolygonArea.
     */
    private List<PolygonArea> createMockPolygons() {
        // Этот метод теперь используется только для создания объектов-заглушек,
        // которые возвращаются при имитации операций записи.
        // Он не используется для получения данных для отображения.
        List<PolygonArea> mockPolygons = new ArrayList<>();
        // Удалены конкретные фиктивные полигоны, так как они не нужны для отображения
        // и не должны быть статичными для имитации записи.
        // При имитации записи, объекты создаются на лету на основе запроса.
        return mockPolygons; // Возвращает пустой список
    }
}
