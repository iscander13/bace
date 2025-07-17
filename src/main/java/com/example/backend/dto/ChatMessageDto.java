package com.example.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageDto {
    private UUID id;
    private String sender;
    private String text;
    private LocalDateTime timestamp;
    // Мы не включаем здесь полные объекты User или PolygonArea,
    // чтобы избежать проблем с сериализацией лениво загруженных прокси.
    // Если вам нужны ID или имена пользователя/полигона, их можно добавить отдельно.
}
