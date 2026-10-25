package com.orbitcore.block.entity;

import com.orbitcore.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/** Голографический планшет: какую запись он хранит. Клиенту нужна запись, чтобы показать её заголовок голограммой. */
public class HoloTabletBlockEntity extends BlockEntity {
    private String log = "";

    public HoloTabletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLO_TABLET, pos, state);
    }

    public String log() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        log = nbt.getString("Log");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("Log", log);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
