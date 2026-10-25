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

public class FuelRefineryBlock extends MachineBlock {
    public FuelRefineryBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.FUEL_REFINERY;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) {
            return;
        }
        if (random.nextDouble() < 0.1) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.3f, 0.6f, false);
        }
        // Пар из колонны синтеза
        world.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.7, pos.getY() + 1.05, pos.getZ() + 0.3,
                0.0, 0.03 + random.nextDouble() * 0.02, 0.0);
    }
}
