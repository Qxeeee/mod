package com.orbitcore.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.orbitcore.client.flight.MeteorFx;
import com.orbitcore.client.sound.VacuumAudio;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * В вакууме звук не идёт по воздуху: всё, кроме музыки и интерфейса,
 * слышно глухо — только то, что передаётся через скафандр.
 */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @WrapOperation(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"))
    private float orbitcore$muffleOnPlay(SoundSystem system, float volume, SoundCategory category, Operation<Float> original,
                                         @Local(argsOnly = true) SoundInstance sound) {
        return original.call(system, volume, category) * VacuumAudio.factor(sound) * MeteorFx.duck(sound);
    }

    @ModifyReturnValue(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"))
    private float orbitcore$muffleOnTick(float volume, SoundInstance sound) {
        return volume * VacuumAudio.factor(sound) * MeteorFx.duck(sound);
    }
}
