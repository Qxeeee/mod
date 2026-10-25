package com.orbitcore.survival;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Состояние жизнеобеспечения игрока: накопленная доза радиации и то, что
 * сервер узнал об окружении (воздух, температура, фон). Клиент получает
 * копию для HUD скафандра; запас кислорода он считает сам по предметам.
 */
public class LifeSupportComponent implements AutoSyncedComponent {
    private final PlayerEntity player;
    /** Накопленная доза, мЗв. */
    public float dose;
    /** Радиационный фон сейчас, мЗв/с (с учётом защиты). */
    public float doseRate;
    /** Температура снаружи, °C. */
    public int temperature = 20;
    /** Можно ли дышать без скафандра. */
    public boolean breathable = true;
    /** Игроку нечем дышать: тратится запас воздуха, а потом здоровье. */
    public boolean suffocating;

    public LifeSupportComponent(PlayerEntity player) {
        this.player = player;
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        dose = tag.getFloat("Dose");
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        tag.putFloat("Dose", dose);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return player == this.player;
    }

    @Override
    public void writeSyncPacket(PacketByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeFloat(dose);
        buf.writeFloat(doseRate);
        buf.writeVarInt(temperature);
        buf.writeBoolean(breathable);
        buf.writeBoolean(suffocating);
    }

    @Override
    public void applySyncPacket(PacketByteBuf buf) {
        dose = buf.readFloat();
        doseRate = buf.readFloat();
        temperature = buf.readVarInt();
        breathable = buf.readBoolean();
        suffocating = buf.readBoolean();
    }
}
