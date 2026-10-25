package com.orbitcore.mixin;

import com.orbitcore.world.ModDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Выброшенные предметы на Луне тоже падают медленно.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = -0.04))
    private double orbitcore$gravity(double gravity) {
        return gravity * ModDimensions.gravity(((Entity) (Object) this).getWorld());
    }
}
