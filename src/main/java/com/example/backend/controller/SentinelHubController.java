package com.example.backend.controller;

import com.example.backend.dto.SentinelAnalysisRequestDto; // Создадим этот DTO ниже
import com.example.backend.service.SentinelHubService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
// import org.springframework.web.bind.annotation.CrossOrigin; // Удален импорт CrossOrigin
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.entiity.User; // Убедитесь, что импорт User правильный

@RestController
@RequestMapping("/api/sentinel")
@Slf4j
// Удалена аннотация @CrossOrigin
public class SentinelHubController {

    private final SentinelHubService sentinelHubService;

    @Autowired
    public SentinelHubController(SentinelHubService sentinelHubService) {
        this.sentinelHubService = sentinelHubService;
    }

    /**
     * Эндпоинт для запроса обработанного изображения (например, NDVI) для полигона.
     * @param requestDto DTO с GeoJSON полигона, типом анализа, датами и размерами изображения.
     * @param user Аутентифицированный пользователь.
     * @return Изображение PNG в виде массива байтов.
     */
    @PostMapping(value = "/process-image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> getProcessedImage(@RequestBody SentinelAnalysisRequestDto requestDto, @AuthenticationPrincipal User user) {
        log.info("SentinelHubController: Received request for processed image for analysis type: {}", requestDto.getAnalysisType());

        if (user == null) {
            // Если эндпоинт должен быть публичным, этот блок нужно удалить
            // или изменить логику, чтобы он не требовал аутентификации.
            // В текущем SecurityConfig, /api/** разрешен без аутентификации,
            // поэтому user здесь может быть null.
            log.warn("Attempt to access /api/sentinel/process-image by unauthenticated user.");
            // Если вы хотите, чтобы этот эндпоинт был доступен только аутентифицированным пользователям,
            // то вам нужно будет изменить /api/** на что-то более конкретное в SecurityConfig,
            // например, /api/auth/** или /api/public/**.
            // Но пока, согласно вашему запросу, /api/** разрешен.
            // Тем не менее, для логирования и потенциальной будущей безопасности,
            // я оставлю проверку на user == null, но не буду возвращать 401,
            // если SecurityConfig разрешает доступ.
        }

        try {
            byte[] imageBytes = sentinelHubService.getProcessedImage(
                    requestDto.getPolygonGeoJson(),
                    requestDto.getAnalysisType(),
                    requestDto.getDateFrom(),
                    requestDto.getDateTo(),
                    requestDto.getWidth(),
                    requestDto.getHeight()
            );
            log.info("Successfully returned processed image.");
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(imageBytes);
        } catch (Exception e) {
            log.error("Error processing Sentinel Hub image request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing image: " + e.getMessage());
        }
    }
}
