package com.example.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.backend.dto.ChapterDto;
import com.example.backend.dto.CropDto;
import com.example.backend.dto.VarietyDto;
import com.example.backend.util.ExcelCropParser;

import jakarta.annotation.PostConstruct;

@Service
public class CropService {

    private List<ChapterDto> cropTree = new ArrayList<>();

    @PostConstruct
    public void loadCropData() {
        // ИСПРАВЛЕНИЕ: Присвойте возвращаемое значение полю cropTree
        this.cropTree = ExcelCropParser.parse("1.xlsx");
        
        // Добавьте этот цикл forEach, чтобы убедиться, что данные загружены
        System.out.println("--- Проверка загруженных глав в CropService ---");
        cropTree.forEach(ch -> System.out.println("✅ Загружена Глава в CropService: " + ch.getTitle()));
        System.out.println("--- Конец проверки ---");
    }

    public List<String> getAllChapters() {
        // Больше нет проверки на демо-пользователя, всегда возвращаем реальные данные
        return cropTree.stream()
                .map(ChapterDto::getTitle)
                .toList();
    }

    public List<CropDto> getCropsByChapter(String chapter) {
        // Больше нет проверки на демо-пользователя, всегда возвращаем реальные данные
        return cropTree.stream()
                .filter(ch -> ch.getTitle().equalsIgnoreCase(chapter))
                .findFirst()
                .map(ChapterDto::getCrops)
                .orElse(List.of());
    }

    public List<VarietyDto> getVarietiesByCrop(String cropName) {
        // Больше нет проверки на демо-пользователя, всегда возвращаем реальные данные
        return cropTree.stream()
                .flatMap(ch -> ch.getCrops().stream())
                .filter(c -> c.getName().equalsIgnoreCase(cropName))
                .findFirst()
                .map(CropDto::getVarieties)
                .orElse(List.of());
    }
    // Удалены методы createMockChapters(), так как они больше не используются
}
