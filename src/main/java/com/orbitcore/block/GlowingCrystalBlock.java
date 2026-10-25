package com.orbitcore.block;

import com.orbitcore.registry.ModSounds;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Светящиеся кристаллы вокруг Эха. Растут пучком, как друза, мерцают
 * и тихо звенят, когда рядом кто-то есть.
 */
public class GlowingCrystalBlock extends AmethystClusterBlock {
    public GlowingCrystalBlock(Settings settings) {
        super(7, 3, settings);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(4) == 0) {
            world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.4 + random.nextDouble() * 0.4,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01 + random.nextDouble() * 0.02, 0);
        }
        if (random.nextInt(60) == 0 && world.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, false) != null) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.ECHO_CRYSTAL, SoundCategory.BLOCKS,
                    0.6f, 0.8f + random.nextFloat() * 0.4f, false);
        }
    }
}
