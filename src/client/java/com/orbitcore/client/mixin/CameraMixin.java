package com.orbitcore.client.mixin;

import com.orbitcore.client.flight.CameraShot;
import com.orbitcore.client.flight.FlightCinematics;
import com.orbitcore.client.flight.QuakeShake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Во время полёта камеру ставит кинорежиссёр {@link FlightCinematics}, а не игрок.
 * При лунотрясении камера дрожит ({@link QuakeShake}).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void orbitcore$cinematicCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                           float tickDelta, CallbackInfo ci) {
        CameraShot shot = FlightCinematics.cameraShot(tickDelta);
        if (shot != null) {
            setRotation(shot.yaw(), shot.pitch());
            setPos(shot.x(), shot.y(), shot.z());
            return;
        }
        // Лунотрясение: взгляд дрожит, но управление у игрока остаётся
        float[] quake = QuakeShake.offset(tickDelta);
        if (quake != null) {
            Camera camera = (Camera) (Object) this;
            setRotation(camera.getYaw() + quake[0], camera.getPitch() + quake[1]);
        }
    }
}
