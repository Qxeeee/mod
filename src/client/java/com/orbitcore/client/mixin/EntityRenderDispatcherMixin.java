package com.orbitcore.client.mixin;

import com.orbitcore.entity.RocketEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Экипаж сидит внутри капсулы — снаружи его не видно.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void orbitcore$hideCrew(E entity, Frustum frustum, double x, double y, double z,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (entity.getVehicle() instanceof RocketEntity) {
            cir.setReturnValue(false);
        }
    }
}
