package com.orbitcore.client.flight;

/**
 * Положение и направление кинокамеры на один кадр.
 */
public record CameraShot(double x, double y, double z, float yaw, float pitch) {
    /** Камера в точке (x, y, z), смотрящая на точку (tx, ty, tz). */
    public static CameraShot lookAt(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new CameraShot(x, y, z, yaw, pitch);
    }

    public CameraShot shaken(float yawOffset, float pitchOffset) {
        return new CameraShot(x, y, z, yaw + yawOffset, pitch + pitchOffset);
    }
}
