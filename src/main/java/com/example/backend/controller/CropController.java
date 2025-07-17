package com.example.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.CropDto;
import com.example.backend.dto.VarietyDto;
import com.example.backend.service.CropService;

@RestController
@RequestMapping("/api/v1/crops")
public class CropController {

    private final CropService cropService;

    public CropController(CropService cropService) {
        this.cropService = cropService;
    }

    @GetMapping("/chapters")
    public List<String> getChapters() {
        return cropService.getAllChapters();
    }

    @GetMapping("/by-chapter")
    public List<CropDto> getCropsByChapter(@RequestParam String chapter) {
        return cropService.getCropsByChapter(chapter);
    }

    // ИСПРАВЛЕНИЕ: Изменено имя параметра в @RequestParam с "name" на "crop"
    @GetMapping("/by-crop")
    public List<VarietyDto> getVarietiesByCrop(@RequestParam("crop") String cropName) {
        return cropService.getVarietiesByCrop(cropName);
    }
}
