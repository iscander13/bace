package com.example.backend.entiity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "polygon_areas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolygonArea {

    @Id
    private UUID id;

    // Добавляем отдельные поля для name, crop и comment
    @Column(name = "name")
    private String name;

    @Column(name = "crop", columnDefinition = "TEXT")
    private String crop;

    @Column(name = "comment", columnDefinition = "TEXT") // Новое поле для комментария
    private String comment;

    @Column(name = "color", columnDefinition = "VARCHAR(7)") // НОВОЕ ПОЛЕ: для хранения HEX-кода цвета (например, #RRGGBB)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "geo_json", columnDefinition = "TEXT") // Теперь это поле будет содержать только GeoJSON Geometry
    private String geoJson;
}
