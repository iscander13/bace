package com.example.backend.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam; // ИМПОРТИРУЕМ RequestParam
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.PolygonAreaResponseDto;
import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.service.PolygonService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/polygons")
@Slf4j
public class PolygonAreaController {

    private final PolygonService polygonService;

    public PolygonAreaController(PolygonService polygonService) {
        this.polygonService = polygonService;
    }

    // Создание нового полигона
    @PostMapping("/create")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<PolygonAreaResponseDto> createPolygon(@RequestBody PolygonRequestDto polygonRequestDto,
                                                                @RequestParam(required = false) Long targetUserId) {
        log.info("PolygonAreaController: Received request to create polygon for targetUserId: {}", targetUserId);
        try {
            PolygonArea createdPolygon = polygonService.createPolygon(polygonRequestDto, targetUserId);
            return new ResponseEntity<>(new PolygonAreaResponseDto(createdPolygon), HttpStatus.CREATED);
        } catch (SecurityException e) {
            log.warn("Security exception during polygon creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new PolygonAreaResponseDto(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("Runtime exception during polygon creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new PolygonAreaResponseDto(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating polygon: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new PolygonAreaResponseDto("Failed to create polygon due to internal server error: " + e.getMessage()));
        }
    }

    // Получение всех полигонов текущего пользователя
    @GetMapping("/user")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> getPolygonsForCurrentUser() {
        log.info("PolygonAreaController: Received request to get polygons for current user.");
        try {
            List<PolygonAreaResponseDto> polygons = polygonService.getPolygonsForCurrentUser(null).stream()
                    .map(PolygonAreaResponseDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(polygons);
        } catch (SecurityException e) {
            log.warn("Security exception during getPolygonsForCurrentUser: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error getting polygons for current user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get polygons due to internal server error: " + e.getMessage());
        }
    }

    // Получение полигонов для конкретного пользователя (только для ADMIN/SUPER_ADMIN)
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> getPolygonsByUserId(@PathVariable Long userId) {
        log.info("PolygonAreaController: Received request to get polygons for user ID: {}", userId);
        try {
            List<PolygonAreaResponseDto> polygons = polygonService.getPolygonsForCurrentUser(userId).stream()
                    .map(PolygonAreaResponseDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(polygons);
        } catch (SecurityException e) {
            log.warn("Security exception during getPolygonsByUserId for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Runtime exception during getPolygonsByUserId for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error getting polygons for user ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get polygons due to internal server error: " + e.getMessage());
        }
    }

    // Обновление полигона
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<PolygonAreaResponseDto> updatePolygon(@PathVariable UUID id, @RequestBody PolygonRequestDto polygonRequestDto) {
        log.info("PolygonAreaController: Received request to update polygon with ID: {}", id);
        try {
            PolygonArea updatedPolygon = polygonService.updatePolygon(id, polygonRequestDto);
            return ResponseEntity.ok(new PolygonAreaResponseDto(updatedPolygon));
        } catch (SecurityException e) {
            log.warn("Security exception during polygon update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new PolygonAreaResponseDto(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("Runtime exception during polygon update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new PolygonAreaResponseDto(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating polygon with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new PolygonAreaResponseDto("Failed to update polygon due to internal server error: " + e.getMessage()));
        }
    }

    // Удаление полигона
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deletePolygon(@PathVariable UUID id) {
        log.info("PolygonAreaController: Received request to delete polygon with ID: {}", id);
        try {
            polygonService.deletePolygon(id);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            log.warn("Security exception during polygon deletion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Runtime exception during polygon deletion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting polygon with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete polygon due to internal server error: " + e.getMessage());
        }
    }

    // Очистка всех полигонов текущего пользователя
    @DeleteMapping("/clear-all")
    @Transactional
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteAllPolygonsForUser(@AuthenticationPrincipal User principalUser) {
        log.info("PolygonAreaController: Received request to clear all polygons for user.");

        try {
            polygonService.clearAllPolygonsForCurrentUser();
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            log.warn("Security exception during clear-all operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error clearing all polygons: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to clear all polygons due to internal server error: " + e.getMessage());
        }
    }
}
