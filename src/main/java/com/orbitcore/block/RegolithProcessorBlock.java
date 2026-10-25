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

public class RegolithProcessorBlock extends MachineBlock {
    public RegolithProcessorBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.REGOLITH_PROCESSOR;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) {
            return;
        }
        if (random.nextDouble() < 0.1) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.3f, 0.6f, false);
        }
        // Пыль из загрузочной воронки сверху
        world.addParticle(ParticleTypes.ASH, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.05,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
    }
}
