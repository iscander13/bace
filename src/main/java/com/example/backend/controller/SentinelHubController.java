// src/main/java/com/example/backend/controller/SentinelHubController.java
package com.example.backend.controller;

import com.example.backend.dto.SentinelAnalysisRequestDto;
import com.example.backend.service.SentinelHubService;
import com.example.backend.entiity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper; // Импорт ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode; // Импорт ObjectNode

@RestController
@RequestMapping("/api/sentinel")
@Slf4j
public class SentinelHubController {

    private final SentinelHubService sentinelHubService;
    private final ObjectMapper objectMapper; // Добавляем ObjectMapper

    @Autowired
    public SentinelHubController(SentinelHubService sentinelHubService, ObjectMapper objectMapper) {
        this.sentinelHubService = sentinelHubService;
        this.objectMapper = objectMapper; // Инициализируем ObjectMapper
    }

    @PostMapping(value = "/process-image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> getProcessedImage(@RequestBody SentinelAnalysisRequestDto requestDto, @AuthenticationPrincipal User user) {
        log.info("SentinelHubController: Received request for processed image for analysis type: {}", requestDto.getAnalysisType());

        if (user == null) {
            log.warn("Attempt to access /api/sentinel/process-image by unauthenticated user.");
            // Используем ObjectMapper для безопасного формирования JSON-ответа об ошибке
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "Authentication required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorNode.toString());
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
            // Используем ObjectMapper для безопасного формирования JSON-ответа об ошибке
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "Error processing image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorNode.toString());
        }
    }
}
