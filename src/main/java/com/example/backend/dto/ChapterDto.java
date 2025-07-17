package com.example.backend.dto;

import java.util.List;


public class ChapterDto {
    private String title;
    private List<CropDto> crops;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<CropDto> getCrops() { return crops; }
    public void setCrops(List<CropDto> crops) { this.crops = crops; }
}