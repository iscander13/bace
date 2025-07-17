package com.example.backend.entiity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Генерировать UUID для ID
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "polygon_id", nullable = false)
    private PolygonArea polygonArea; // Связь с полигоном

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Связь с пользователем

    @Column(name = "sender", nullable = false) // 'user' или 'ai'
    private String sender;

    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp; // Время отправки сообщения
}