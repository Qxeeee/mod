package com.orbitcore.world;

import com.mojang.serialization.Codec;
import com.orbitcore.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Ударный кратер: чаша, приподнятый вал и светлые выбросы вокруг. У свежих кратеров
 * дно присыпано светлым реголитом, а в центре иногда лежит железный метеорит.
 */
public class CraterFeature extends Feature<DefaultFeatureConfig> {
    /** Радиус вместе с валом не выходит за соседние чанки. */
    private static final int MAX_RADIUS = 11;

    public CraterFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        Random random = context.getRandom();
        BlockPos origin = context.getOrigin();
        int radius = 3 + random.nextInt(MAX_RADIUS - 2);
        double depth = radius * (0.35 + random.nextDouble() * 0.15);
        double rim = Math.max(1, radius * 0.18);
        int outer = (int) Math.ceil(radius * 1.4);
        BlockState floor = ModBlocks.REGOLITH.getDefaultState();

        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.4;
                if (d > outer) {
                    continue;
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
                BlockPos surface = new BlockPos(x, top, z);
                BlockState surfaceState = world.getBlockState(surface);
                if (surfaceState.isAir()) {
                    continue;
                }
                if (d < radius) {
                    // Чаша: парабола от центра к краю
                    int cut = (int) Math.round(depth * (1 - (d / radius) * (d / radius)) + rim * (d / radius));
                    for (int i = 0; i < cut; i++) {
                        world.setBlockState(surface.down(i), Blocks.AIR.getDefaultState(), 2);
                    }
                    world.setBlockState(surface.down(cut), floor, 2);
                } else {
                    // Вал и выбросы, сходящие на нет
                    double t = (d - radius) / (outer - radius);
                    int raise = (int) Math.round(rim * (1 - t) * 1.6);
                    for (int i = 1; i <= raise; i++) {
                        world.setBlockState(surface.up(i), surfaceState, 2);
                    }
                    if (random.nextDouble() < 0.35 * (1 - t)) {
                        world.setBlockState(surface.up(raise), floor, 2);
                    }
                }
            }
        }
        if (radius >= 6 && random.nextInt(4) == 0) {
            int bottom = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
            world.setBlockState(new BlockPos(origin.getX(), bottom, origin.getZ()), Blocks.RAW_IRON_BLOCK.getDefaultState(), 2);
        }
        return true;
    }
}
