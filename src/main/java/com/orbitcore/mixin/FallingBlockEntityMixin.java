package com.orbitcore.mixin;

import com.orbitcore.world.ModDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Падающие блоки на Луне летят по медленным дугам — так выглядит выброс грунта при ударе метеорита.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = -0.04))
    private double orbitcore$gravity(double gravity) {
        return gravity * ModDimensions.gravity(((Entity) (Object) this).getWorld());
    }
}
