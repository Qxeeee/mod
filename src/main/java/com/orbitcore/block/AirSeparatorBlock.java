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

public class AirSeparatorBlock extends MachineBlock {
    public AirSeparatorBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.AIR_SEPARATOR;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) {
            return;
        }
        if (random.nextDouble() < 0.08) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.BLOCK_POWDER_SNOW_STEP, SoundCategory.BLOCKS, 0.4f, 0.5f, false);
        }
        // Холодный пар стекает вниз по стенкам
        double x = pos.getX() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        world.addParticle(ParticleTypes.SNOWFLAKE, x, pos.getY() + 1.0, z, 0.0, -0.02, 0.0);
        if (random.nextBoolean()) {
            world.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 0.9, z, 0.0, -0.01, 0.0);
        }
    }
}
