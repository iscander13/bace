// src/main/java/com/example/backend/dto/SentinelAnalysisRequestDto.java
package com.example.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO для запросов анализа Sentinel Hub и Agromonitoring.
 * Содержит данные для определения области, диапазона дат и типа анализа.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentinelAnalysisRequestDto {
    // Для Sentinel Hub Process API (изображения)
    private String polygonGeoJson; 
    private String analysisType;

    // Для Agromonitoring API (статистика NDVI)
    private String polygonId; // НОВОЕ ПОЛЕ: ID полигона для Agromonitoring

    private String dateFrom;
    private String dateTo;
    
    // Эти поля используются для Sentinel Hub Process API (изображения),
    // но не используются напрямую для Agromonitoring NDVI History API.
    private int width; 
    private int height; 
}
