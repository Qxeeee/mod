package com.orbitcore.block;

import com.orbitcore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Секция стартового стола. Стол — квадрат 3×3 из таких блоков; ракета ставится на центральный.
 */
public class LaunchPadBlock extends Block {
    public LaunchPadBlock(Settings settings) {
        super(settings);
    }

    /** Собран ли стол 3×3 с центром в {@code center}. */
    public static boolean isComplete(BlockView world, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.getBlockState(center.add(dx, 0, dz)).isOf(ModBlocks.LAUNCH_PAD)) {
                    return false;
                }
            }
        }
        return true;
    }
}
