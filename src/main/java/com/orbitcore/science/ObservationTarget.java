package com.orbitcore.science;

import org.jetbrains.annotations.Nullable;

/**
 * Цели для телескопа. Каждую можно снять один раз за ночь.
 *
 * <p>Небо в телескопе — развёртка: азимут 0–3600 (десятые доли градуса), высота 0–900.
 * Положение Луны зависит от времени ночи, созвездия стоят на месте.
 *
 * @param stars точки созвездия относительно центра, в тех же единицах, что и небо
 */
public enum ObservationTarget {
    MOON("moon", 5, 2.0f, CelestialBody.MOON, 0, 0, new int[0][]),
    ORION("orion", 2, 1.0f, null, 2050, 330, new int[][]{
            {-60, -110}, {70, -100}, {-25, 0}, {0, 8}, {25, 16}, {-70, 120}, {80, 110}, {0, 60}}),
    CASSIOPEIA("cassiopeia", 2, 1.0f, null, 420, 640, new int[][]{
            {-110, 10}, {-55, -40}, {0, 5}, {55, -45}, {110, 0}}),
    CYGNUS("cygnus", 2, 1.0f, null, 1350, 700, new int[][]{
            {0, -120}, {0, -40}, {0, 30}, {0, 110}, {-95, -10}, {95, -30}});

    public static final int SKY_WIDTH = 3600;
    public static final int SKY_HEIGHT = 900;

    private final String id;
    private final int points;
    private final float minZoom;
    @Nullable
    private final CelestialBody discovers;
    private final int azimuth;
    private final int altitude;
    private final int[][] stars;

    ObservationTarget(String id, int points, float minZoom, @Nullable CelestialBody discovers,
                      int azimuth, int altitude, int[][] stars) {
        this.id = id;
        this.points = points;
        this.minZoom = minZoom;
        this.discovers = discovers;
        this.azimuth = azimuth;
        this.altitude = altitude;
        this.stars = stars;
    }

    public String id() {
        return id;
    }

    /** Очки наблюдений за снимок. */
    public int points() {
        return points;
    }

    /** Минимальное увеличение, при котором цель можно снять. */
    public float minZoom() {
        return minZoom;
    }

    @Nullable
    public CelestialBody discovers() {
        return discovers;
    }

    public int[][] stars() {
        return stars;
    }

    public String translationKey() {
        return "target.orbitcore." + id;
    }

    /** Азимут цели в момент {@code timeOfDay} (0–24000). */
    public double azimuthAt(long timeOfDay) {
        if (this != MOON) {
            return azimuth;
        }
        return 900 + 1800 * nightProgress(timeOfDay);
    }

    /** Высота цели над горизонтом в момент {@code timeOfDay}. */
    public double altitudeAt(long timeOfDay) {
        if (this != MOON) {
            return altitude;
        }
        return 60 + 640 * Math.sin(Math.PI * nightProgress(timeOfDay));
    }

    /** Ночь в Minecraft — с 13000 до 23000 тиков суток. */
    public static boolean isNight(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= 13000 && t <= 23000;
    }

    private static double nightProgress(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return Math.max(0, Math.min(1, (t - 13000) / 10000.0));
    }

    @Nullable
    public static ObservationTarget byId(String id) {
        for (ObservationTarget target : values()) {
            if (target.id.equals(id)) {
                return target;
            }
        }
        return null;
    }
}
