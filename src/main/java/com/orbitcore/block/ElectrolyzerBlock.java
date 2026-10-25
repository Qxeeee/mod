package com.orbitcore.block;

import com.orbitcore.block.entity.MachineBlockEntity;
import com.orbitcore.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class ElectrolyzerBlock extends MachineBlock {
    public ElectrolyzerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ELECTROLYZER;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.2) {
            world.playSound(x, y, z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.4f, 1.4f, false);
        }
        // Искры с электродов
        world.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (random.nextBoolean() ? 0.19 : -0.19), y + 0.05, z, 0.0, 0.05, 0.0);
    }
}
