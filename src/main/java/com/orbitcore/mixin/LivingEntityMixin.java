package com.orbitcore.mixin;

import com.orbitcore.registry.ModComponents;
import com.orbitcore.world.ModDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Сила тяжести на Луне: игроки и мобы прыгают выше и падают медленнее,
 * а урон от падения меньше во столько же раз. И дыхание в вакууме.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.08))
    private double orbitcore$gravity(double gravity) {
        return gravity * ModDimensions.gravity(((Entity) (Object) this).getWorld());
    }

    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float orbitcore$fallDistance(float fallDistance) {
        return (float) (fallDistance * ModDimensions.gravity(((Entity) (Object) this).getWorld()));
    }

    /** В вакууме запас воздуха не восстанавливается — его тратит {@link com.orbitcore.survival.LifeSupport}. */
    @Inject(method = "getNextAirOnLand", at = @At("HEAD"), cancellable = true)
    private void orbitcore$noAirInVacuum(int air, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof PlayerEntity player && ModComponents.LIFE_SUPPORT.get(player).suffocating) {
            cir.setReturnValue(air);
        }
    }
}
