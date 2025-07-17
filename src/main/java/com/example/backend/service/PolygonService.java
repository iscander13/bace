package com.example.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository; // Импортируем ChatMessageRepository

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolygonService {

    private final PolygonAreaRepository polygonAreaRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository; // Внедряем ChatMessageRepository

    // Helper method to get the current authenticated user
    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("PolygonService: User not authenticated.");
            throw new IllegalStateException("Пользователь не аутентифицирован.");
        }

        Object principal = authentication.getPrincipal();
        String userEmail;

        if (principal instanceof User castedUser) {
            userEmail = castedUser.getEmail();
        } else if (principal instanceof org.springframework.security.core.userdetails.User springUser) {
            userEmail = springUser.getUsername();
        } else {
            log.error("PolygonService: Unknown principal type: {}", principal.getClass().getName());
            throw new IllegalStateException("Неизвестный тип принципала.");
        }

        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден в базе данных."));
    }

    // Create a new polygon
    @Transactional
    public PolygonArea createPolygon(PolygonRequestDto polygonRequestDto, Long targetUserId) {
        User currentUser = getCurrentAuthenticatedUser();
        log.info("Current authenticated user: ID={}, Role={}, Email={}", currentUser.getId(), currentUser.getRole(), currentUser.getEmail());

        User ownerUser;
        if (targetUserId != null) {
            // Если указан targetUserId, то только ADMIN или SUPER_ADMIN могут создавать полигоны для других
            if (!"ADMIN".equals(currentUser.getRole()) && !"SUPER_ADMIN".equals(currentUser.getRole())) {
                log.warn("USER (ID: {}) attempted to create polygon for targetUserId: {} without ADMIN/SUPER_ADMIN role.", currentUser.getId(), targetUserId);
                throw new SecurityException("У вас нет прав для создания полигонов для других пользователей.");
            }
            ownerUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new RuntimeException("Целевой пользователь не найден."));
            log.info("ADMIN/SUPER_ADMIN (ID: {}) creating polygon for target user (ID: {})", currentUser.getId(), targetUserId);
        } else {
            // Если targetUserId не указан, полигон создается для текущего пользователя
            ownerUser = currentUser;
            log.info("User (ID: {}) creating polygon for themselves.", currentUser.getId());
        }

        PolygonArea polygonArea = PolygonArea.builder()
                .id(polygonRequestDto.getId()) // ✨ ИСПРАВЛЕНО: Добавлено ID из DTO
                .name(polygonRequestDto.getName())
                .comment(polygonRequestDto.getComment())
                .color(polygonRequestDto.getColor())
                .crop(polygonRequestDto.getCrop())
                .geoJson(polygonRequestDto.getGeoJson())
                .user(ownerUser) // Устанавливаем владельца полигона
                .build();
        return polygonAreaRepository.save(polygonArea);
    }

    // Get all polygons for the current user or a target user (for ADMIN/SUPER_ADMIN)
    public List<PolygonArea> getPolygonsForCurrentUser(Long targetUserId) {
        User currentUser = getCurrentAuthenticatedUser();
        String currentUserRole = currentUser.getRole();
        log.info("PolygonService: getPolygonsForCurrentUser called by user ID: {} with role: {}", currentUser.getId(), currentUserRole);

        if ("USER".equals(currentUserRole)) {
            // Обычный пользователь может видеть только свои полигоны
            if (targetUserId != null && !currentUser.getId().equals(targetUserId)) {
                log.warn("USER (ID: {}) attempted to view polygons of target user (ID: {}). Access denied.", currentUser.getId(), targetUserId);
                throw new SecurityException("У вас нет разрешения на просмотр полигонов других пользователей.");
            }
            log.info("USER (ID: {}) fetching their own polygons.", currentUser.getId());
            return polygonAreaRepository.findByUser_Id(currentUser.getId());
        } else if ("ADMIN".equals(currentUserRole) || "SUPER_ADMIN".equals(currentUserRole)) {
            if (targetUserId != null) {
                // ADMIN/SUPER_ADMIN просматривает полигоны конкретного пользователя
                User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new RuntimeException("Целевой пользователь не найден."));
                
                // ADMIN может просматривать полигоны USER, но не других ADMIN или SUPER_ADMIN
                if ("ADMIN".equals(currentUserRole) && ("ADMIN".equals(targetUser.getRole()) || "SUPER_ADMIN".equals(targetUser.getRole()))) {
                    // Разрешаем ADMIN просматривать СВОИ собственные полигоны, если targetUserId совпадает с его ID
                    if (currentUser.getId().equals(targetUserId)) {
                        log.info("ADMIN (ID: {}) fetching their own polygons.", currentUser.getId());
                        return polygonAreaRepository.findByUser_Id(currentUser.getId());
                    } else {
                        log.warn("ADMIN (ID: {}) attempted to view polygons of user (ID: {}) with role {}. Access denied.", currentUser.getId(), targetUserId, targetUser.getRole());
                        throw new SecurityException("У вас нет разрешения на просмотр полигонов пользователей с ролью " + targetUser.getRole() + ".");
                    }
                }
                log.info("{} (ID: {}) fetching polygons for target user (ID: {}) with role {}.", currentUserRole, currentUser.getId(), targetUserId, targetUser.getRole());
                return polygonAreaRepository.findByUser_Id(targetUserId);
            } else {
                // ADMIN/SUPER_ADMIN просматривает свои собственные полигоны (если targetUserId не указан)
                log.info("{} (ID: {}) fetching their own polygons (targetUserId not specified).", currentUserRole, currentUser.getId());
                return polygonAreaRepository.findByUser_Id(currentUser.getId());
            }
        } else {
            log.warn("Unknown user role {} (ID: {}) attempted to fetch polygons. Access denied.", currentUserRole, currentUser.getId());
            throw new SecurityException("Недостаточно прав для выполнения операции.");
        }
    }


    // Update an existing polygon
    @Transactional
    public PolygonArea updatePolygon(UUID polygonId, PolygonRequestDto polygonRequestDto) {
        User currentUser = getCurrentAuthenticatedUser();
        String currentUserRole = currentUser.getRole();
        log.info("Attempting to update polygon ID: {} by user ID: {} with role: {}", polygonId, currentUser.getId(), currentUserRole);

        PolygonArea existingPolygon = polygonAreaRepository.findById(polygonId)
                .orElseThrow(() -> new RuntimeException("Полигон не найден."));

        // Проверка прав доступа
        if ("SUPER_ADMIN".equals(currentUserRole)) {
            log.info("SUPER_ADMIN (ID: {}) updating polygon ID: {} owned by USER (ID: {})", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
            // SUPER_ADMIN может редактировать любые полигоны
        } else if ("ADMIN".equals(currentUserRole)) {
            // ADMIN может редактировать свои полигоны ИЛИ полигоны USERов
            if (existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.info("ADMIN (ID: {}) updating their own polygon ID: {}", currentUser.getId(), polygonId);
            } else if ("USER".equals(existingPolygon.getUser().getRole())) {
                log.info("ADMIN (ID: {}) updating USER's (ID: {}) polygon ID: {}", currentUser.getId(), existingPolygon.getUser().getId(), polygonId);
            } else {
                log.warn("ADMIN (ID: {}) attempted to update polygon ID: {} owned by user with role {}. Access denied.", currentUser.getId(), polygonId, existingPolygon.getUser().getRole());
                throw new SecurityException("Администратор не может редактировать полигоны пользователей с ролью " + existingPolygon.getUser().getRole() + ".");
            }
        } else if ("USER".equals(currentUserRole)) {
            // Обычный пользователь может редактировать только свои полигоны
            if (!existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.warn("USER (ID: {}) attempted to update polygon ID: {} owned by ID: {}. Access denied.", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
                throw new SecurityException("У вас нет разрешения на редактирование этого полигона.");
            }
            log.info("USER (ID: {}) updating their own polygon ID: {}", currentUser.getId(), polygonId);
        } else {
            log.warn("Unknown user role {} (ID: {}) attempted to update polygon ID: {}. Access denied.", currentUserRole, currentUser.getId(), polygonId);
            throw new SecurityException("Недостаточно прав для выполнения операции.");
        }

        existingPolygon.setName(polygonRequestDto.getName());
        existingPolygon.setComment(polygonRequestDto.getComment());
        existingPolygon.setColor(polygonRequestDto.getColor());
        existingPolygon.setCrop(polygonRequestDto.getCrop());
        existingPolygon.setGeoJson(polygonRequestDto.getGeoJson());
        return polygonAreaRepository.save(existingPolygon);
    }

    // Delete a polygon
    @Transactional
    public void deletePolygon(UUID polygonId) {
        User currentUser = getCurrentAuthenticatedUser();
        String currentUserRole = currentUser.getRole();
        log.info("Attempting to delete polygon ID: {} by user ID: {} with role: {}", polygonId, currentUser.getId(), currentUserRole);

        PolygonArea existingPolygon = polygonAreaRepository.findById(polygonId)
                .orElseThrow(() -> new RuntimeException("Полигон не найден."));

        // Проверка прав доступа
        if ("SUPER_ADMIN".equals(currentUserRole)) {
            log.info("SUPER_ADMIN (ID: {}) deleting polygon ID: {} owned by USER (ID: {})", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
        } else if ("ADMIN".equals(currentUserRole)) {
            if (existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.info("ADMIN (ID: {}) deleting their own polygon ID: {}", currentUser.getId(), polygonId);
            } else if ("USER".equals(existingPolygon.getUser().getRole())) {
                log.info("ADMIN (ID: {}) deleting USER's (ID: {}) polygon ID: {}", currentUser.getId(), existingPolygon.getUser().getId(), polygonId);
            } else {
                log.warn("ADMIN (ID: {}) attempted to delete polygon ID: {} owned by user with role {}. Access denied.", currentUser.getId(), polygonId, existingPolygon.getUser().getRole());
                throw new SecurityException("Администратор не может удалять полигоны пользователей с ролью " + existingPolygon.getUser().getRole() + ".");
            }
        } else if ("USER".equals(currentUserRole)) {
            if (!existingPolygon.getUser().getId().equals(currentUser.getId())) {
                log.warn("USER (ID: {}) attempted to delete polygon ID: {} owned by ID: {}", currentUser.getId(), polygonId, existingPolygon.getUser().getId());
                throw new SecurityException("У вас нет разрешения на удаление этого полигона.");
            }
            log.info("USER (ID: {}) deleting their own polygon ID: {}", currentUser.getId(), polygonId);
        } else {
            log.warn("Unknown user role {} (ID: {}) attempted to delete polygon ID: {}", currentUserRole, currentUser.getId(), polygonId);
            throw new SecurityException("Недостаточно прав для выполнения операции.");
        }

        // Удаляем связанные сообщения чата перед удалением полигона
        chatMessageRepository.deleteByPolygonArea_IdAndUser_Id(polygonId, existingPolygon.getUser().getId());
        log.info("Deleted chat messages for polygon ID: {}", polygonId);

        polygonAreaRepository.delete(existingPolygon);
        log.info("Deleted polygon ID: {}", polygonId);
    }

    // Clear all polygons for the current user
    @Transactional
    public void clearAllPolygonsForCurrentUser() {
        User currentUser = getCurrentAuthenticatedUser();
        log.info("Clearing all polygons for user ID: {}", currentUser.getId());
        
        // Удаляем все сообщения чата, связанные с полигонами текущего пользователя
        chatMessageRepository.deleteByUser_Id(currentUser.getId());
        log.info("Deleted all chat messages for user ID: {}", currentUser.getId());

        polygonAreaRepository.deleteByUser_Id(currentUser.getId());
        log.info("Deleted all polygons for user ID: {}", currentUser.getId());
    }
}
