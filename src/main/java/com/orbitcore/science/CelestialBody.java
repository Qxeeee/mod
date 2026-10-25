package com.orbitcore.science;

import org.jetbrains.annotations.Nullable;

/**
 * Небесные тела. Чтобы лететь к телу, его сначала нужно открыть.
 * Новые планеты добавляются сюда по мере расширения мода.
 */
public enum CelestialBody {
    EARTH("earth", true),
    MOON("moon", false);

    private final String id;
    private final boolean knownFromStart;

    CelestialBody(String id, boolean knownFromStart) {
        this.id = id;
        this.knownFromStart = knownFromStart;
    }

    public String id() {
        return id;
    }

    public boolean knownFromStart() {
        return knownFromStart;
    }

    public String translationKey() {
        return "body.orbitcore." + id;
    }

    @Nullable
    public static CelestialBody byId(String id) {
        for (CelestialBody body : values()) {
            if (body.id.equals(id)) {
                return body;
            }
        }
        return null;
    }
}
