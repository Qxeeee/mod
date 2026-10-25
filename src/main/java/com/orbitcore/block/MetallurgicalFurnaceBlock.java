package com.orbitcore.block;

import com.orbitcore.block.entity.MachineBlockEntity;
import com.orbitcore.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class MetallurgicalFurnaceBlock extends MachineBlock {
    public MetallurgicalFurnaceBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.METALLURGICAL_FURNACE;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.15) {
            world.playSound(x, y, z, SoundEvents.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 0.8f, false);
        }
        // Искры и жар из топки, дым из трубы
        Direction facing = state.get(FACING);
        double side = random.nextDouble() * 0.5 - 0.25;
        double dx = facing.getAxis() == Direction.Axis.X ? facing.getOffsetX() * 0.52 : side;
        double dz = facing.getAxis() == Direction.Axis.Z ? facing.getOffsetZ() * 0.52 : side;
        world.addParticle(ParticleTypes.FLAME, x + dx, y + 0.35, z + dz, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) {
            world.addParticle(ParticleTypes.LAVA, x + dx, y + 0.4, z + dz, 0.0, 0.0, 0.0);
        }
        world.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 1.05, z, 0.0, 0.04, 0.0);
    }
}
