package com.example.backend.model;

import java.util.List;

import com.example.backend.dto.ChapterDto;

public class CropTree {

    private List<ChapterDto> chapters;

    public CropTree() {
    }

    public CropTree(List<ChapterDto> chapters) {
        this.chapters = chapters;
    }

    public List<ChapterDto> getChapters() {
        return chapters;
    }

    public void setChapters(List<ChapterDto> chapters) {
        this.chapters = chapters;
    }
}
