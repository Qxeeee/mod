package com.orbitcore.science;

/**
 * Виды очков науки. Каждое исследование стоит какое-то количество каждого вида.
 */
public enum ScienceType {
    /** Телескоп и радиотелескоп. */
    OBSERVATION("observation", 0xFF8FC7FF),
    /** Анализ образцов грунта, руд и живности. */
    SAMPLE("sample", 0xFFF2C66D),
    /** Зонды и спутники. */
    TELEMETRY("telemetry", 0xFF7CF0B0);

    private final String id;
    private final int color;

    ScienceType(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public String translationKey() {
        return "science.orbitcore." + id;
    }
}
