package com.example.backend.dto;

import java.util.List;

public class CropDto {
    private String name;
    private List<VarietyDto> varieties;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<VarietyDto> getVarieties() { return varieties; }
    public void setVarieties(List<VarietyDto> varieties) { this.varieties = varieties; }
}
