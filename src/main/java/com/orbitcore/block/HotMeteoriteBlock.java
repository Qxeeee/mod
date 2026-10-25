package com.orbitcore.block;

import com.orbitcore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Только что упавший метеорит: раскалён докрасна, светится, дымит и обжигает.
 * Через полминуты остывает в обычный метеорит.
 */
public class HotMeteoriteBlock extends Block {
    /** Сколько тиков он остывает. */
    public static final int COOLING_TICKS = 600;

    public HotMeteoriteBlock(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleBlockTick(pos, this, COOLING_TICKS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.setBlockState(pos, ModBlocks.METEORITE.getDefaultState());
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.bypassesSteppingEffects() && entity instanceof LivingEntity) {
            entity.damage(world.getDamageSources().hotFloor(), 2.0f);
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        for (int i = 0; i < 2; i++) {
            world.addParticle(random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.LAVA,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
