package com.example.backend.dto;

import java.util.UUID;

import com.example.backend.entiity.PolygonArea;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Lombok аннотации для удобства (генерируют геттеры, сеттеры, конструкторы и билдер)
@Data // Генерирует геттеры, сеттеры, toString, equals, hashCode
@NoArgsConstructor // Генерирует конструктор без аргументов
@AllArgsConstructor // Генерирует конструктор со всеми аргументами (для всех полей)
@Builder // Генерирует билдер для удобного создания объектов
public class PolygonAreaResponseDto {

    private UUID id;
    private String name;
    private String comment;
    private String color;
    private String crop;
    private Long userId; // ID пользователя, которому принадлежит полигон
    private String geoJson;

    // --- Добавьте этот конструктор для преобразования PolygonArea в DTO ---
    public PolygonAreaResponseDto(PolygonArea polygonArea) {
        this.id = polygonArea.getId();
        this.name = polygonArea.getName();
        this.comment = polygonArea.getComment();
        this.color = polygonArea.getColor();
        this.crop = polygonArea.getCrop();
        this.geoJson = polygonArea.getGeoJson();
        if (polygonArea.getUser() != null) {
            this.userId = polygonArea.getUser().getId();
        }
    }

    // --- Добавьте этот конструктор для возврата сообщения об ошибке (опционально, но решает проблему) ---
    // Это будет использоваться, когда вы возвращаете только сообщение, а не полную информацию о полигоне.
    // Если вы предпочитаете возвращать ResponseEntity<String> для ошибок, этот конструктор не нужен.
    private String message; // Поле для сообщения об ошибке или статусе

    public PolygonAreaResponseDto(String message) {
        this.message = message;
    }
}