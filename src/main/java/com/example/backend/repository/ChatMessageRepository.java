package com.example.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entiity.ChatMessage;

import jakarta.transaction.Transactional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByPolygonArea_IdOrderByTimestampAsc(UUID polygonAreaId);
    List<ChatMessage> findByUser_IdAndPolygonArea_IdOrderByTimestampAsc(Long userId, UUID polygonAreaId);

    // НОВЫЙ МЕТОД: для удаления всех сообщений конкретного пользователя
    @Transactional
    void deleteByUser_Id(Long userId);

    // Метод для удаления всех сообщений по ID полигона и ID пользователя
    @Transactional // Аннотация @Transactional необходима для операций удаления
    void deleteByPolygonArea_IdAndUser_Id(UUID polygonId, Long userId);
}