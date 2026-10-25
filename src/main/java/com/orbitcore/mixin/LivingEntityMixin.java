package com.orbitcore.mixin;

import com.orbitcore.world.ModDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Сила тяжести на Луне: игроки и мобы прыгают выше и падают медленнее,
 * а урон от падения меньше во столько же раз.
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
}
