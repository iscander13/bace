// src/main/java/com/example/backend/dto/SentinelAnalysisRequestDto.java
package com.example.backend.dto;

import lombok.Data;

@Data
public class SentinelAnalysisRequestDto {
    private String polygonGeoJson; // GeoJSON строка геометрии полигона
    private String analysisType;   // Тип анализа (NDVI, TRUE_COLOR и т.д.)
    private String dateFrom;       // Начальная дата (YYYY-MM-DD)
    private String dateTo;         // Конечная дата (YYYY-MM-DD)
    private int width;             // Ширина изображения
    private int height;            // Высота изображения
}
