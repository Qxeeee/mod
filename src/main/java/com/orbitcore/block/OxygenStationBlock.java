package com.orbitcore.block;

import com.orbitcore.block.entity.MachineBlockEntity;
import com.orbitcore.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class OxygenStationBlock extends MachineBlock {
    public OxygenStationBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.OXYGEN_STATION;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) {
            return;
        }
        // Холодный кислород парит из шланга на лицевой стороне
        Direction facing = state.get(FACING);
        double x = pos.getX() + 0.5 + facing.getOffsetX() * 0.55;
        double z = pos.getZ() + 0.5 + facing.getOffsetZ() * 0.55;
        world.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 0.3, z,
                facing.getOffsetX() * 0.02, -0.01, facing.getOffsetZ() * 0.02);
    }
}
