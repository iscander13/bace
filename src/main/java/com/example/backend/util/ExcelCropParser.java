package com.example.backend.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.example.backend.dto.ChapterDto;
import com.example.backend.dto.CropDto;
import com.example.backend.dto.VarietyDto;

public class ExcelCropParser {

    public static List<ChapterDto> parse(String resourcePath) {
        System.out.println("📄 Загрузка Excel: " + resourcePath);

        List<ChapterDto> chapters = new ArrayList<>();

        try (InputStream is = ExcelCropParser.class.getClassLoader().getResourceAsStream(resourcePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            ChapterDto currentChapter = null;
            CropDto currentCrop = null;

            for (Row row : sheet) {
                if (row.getRowNum() < 2) continue; // Это мы уже исправили
                Cell firstCell = row.getCell(0);
                if (firstCell == null) continue;
                String cellText = getString(firstCell);

                // DEBUG: Вывод содержимого ячейки A

                if (cellText.startsWith("Глава")) {
                    currentChapter = new ChapterDto();
                    currentChapter.setTitle(cellText.replaceFirst("Глава \\d+\\. ?", "").trim());
                    currentChapter.setCrops(new ArrayList<>());
                    chapters.add(currentChapter);
                    currentCrop = null;

                } else if (cellText.startsWith("Параграф")) {
                    currentCrop = new CropDto();
                    currentCrop.setName(cellText.replaceFirst("Параграф \\d+\\. ?", "").trim());
                    currentCrop.setVarieties(new ArrayList<>());
                    if (currentChapter != null) {
                        currentChapter.getCrops().add(currentCrop);
                    } 

                } else if (cellText.matches("\\d+\\.?")) { // "1." или "1"
                    try {
                        int number = Integer.parseInt(cellText.replace(".", "").trim());
                        String name = getString(row.getCell(3)); // D
                        String region = getString(row.getCell(5)); // F

                        if (currentCrop != null && name != null && !name.isBlank()) {
                            VarietyDto variety = new VarietyDto();
                            variety.setNumber(number);
                            variety.setName(name);
                            variety.setRegion(region);
                            currentCrop.getVarieties().add(variety);
                            // DEBUG: Проверяем, добавлен ли сорт к параграфу
                        } else if (currentCrop == null) {
                             System.out.println("⚠️ Обнаружен Сорт '" + name + "' до инициализации Параграфа!");
                        } else if (name == null || name.isBlank()) {
                             System.out.println("⚠️ Обнаружен Сорт без имени: " + cellText);
                        }
                    } catch (Exception e) {
                        System.out.println("⚠️ Ошибка при парсинге строки (сорт): " + e.getMessage() + " в ячейке A" + (row.getRowNum() + 1));
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("🚫 Не удалось загрузить Excel: " + resourcePath);
            e.printStackTrace();
        }

        // DEBUG: Конечный размер списка глав перед возвратом
        System.out.println("➡️ Конечный размер списка глав: " + chapters.size());
        return chapters;
    }

    private static String getString(Cell cell) {
        return cell == null ? null : cell.toString().trim();
    }
}
