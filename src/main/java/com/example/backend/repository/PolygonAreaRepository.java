package com.example.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository; // ✨ Убедитесь, что это импортировано
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entiity.PolygonArea;

public interface PolygonAreaRepository extends JpaRepository<PolygonArea, UUID> {

    // ✅ ЭТА СТРОКА ДОЛЖНА БЫТЬ ПРИСУТСТВОВАТЬ
    @EntityGraph(attributePaths = "user")
    List<PolygonArea> findByUser_Id(Long userId);

    @Transactional
    void deleteByUser_Id(Long userId);
}