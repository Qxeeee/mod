package com.orbitcore.client.mixin;

import com.orbitcore.client.flight.FlightCinematics;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.world.ModDimensions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перелёт между Землёй и Луной — не портал в Незер: вместо его гула играет
 * спокойный сигнал бортового компьютера о прибытии.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "processWorldEvent", at = @At("HEAD"), cancellable = true)
    private void orbitcore$arrivalSound(int eventId, BlockPos pos, int data, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (eventId == WorldEvents.TRAVEL_THROUGH_PORTAL && client.world != null && client.player != null
                && (client.world.getRegistryKey() == ModDimensions.MOON || FlightCinematics.lastWorld() == ModDimensions.MOON)) {
            client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.FLIGHT_ARRIVAL, 1.0f, 0.9f));
            ci.cancel();
        }
    }
}
