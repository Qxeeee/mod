package com.orbitcore.world;

import com.mojang.serialization.Codec;
import com.orbitcore.block.HoloTabletBlock;
import com.orbitcore.block.entity.HoloTabletBlockEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.story.RuinLayout;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Руины Строителей: кольцо обломанных колонн из их камня, разбитые плиты пола,
 * кристаллы и в центре — голографический планшет на постаменте. Где именно стоят
 * руины, решает {@link RuinLayout}; фича просто строит их в нужном чанке.
 */
public class RuinFeature extends Feature<DefaultFeatureConfig> {
    public RuinFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        long seed = world.getSeed();
        int cellX = RuinLayout.cell(origin.getX());
        int cellZ = RuinLayout.cell(origin.getZ());
        BlockPos center = RuinLayout.center(seed, cellX, cellZ);
        if (center == null || !new ChunkPos(center).equals(new ChunkPos(origin))) {
            return false;
        }
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, center.getX(), center.getZ());
        build(world, new BlockPos(center.getX(), y, center.getZ()), Random.create(seed ^ center.asLong()), RuinLayout.log(seed, cellX, cellZ));
        return true;
    }

    /** Построить руины с центром на поверхности {@code base}. */
    public static void build(StructureWorldAccess world, BlockPos base, Random random, String log) {
        BlockState stone = ModBlocks.BUILDER_STONE.getDefaultState();
        // Пол: битые плиты вровень с грунтом
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz <= 18 && random.nextFloat() < 0.7f) {
                    BlockPos column = base.add(dx, 0, dz);
                    BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, column).down();
                    world.setBlockState(top, stone, 2);
                }
            }
        }
        // Кольцо колонн, большинство обломано
        for (int i = 0; i < 8; i++) {
            float angle = i / 8f * MathHelper.TAU + random.nextFloat() * 0.2f;
            BlockPos column = base.add(Math.round(MathHelper.cos(angle) * 6), 0, Math.round(MathHelper.sin(angle) * 6));
            BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, column);
            int height = random.nextFloat() < 0.25f ? 0 : 1 + random.nextInt(5);
            for (int h = 0; h < height; h++) {
                world.setBlockState(top.up(h), stone, 2);
            }
        }
        // Пара упавших колонн
        for (int i = 0; i < 2; i++) {
            Direction along = Direction.Type.HORIZONTAL.random(random);
            BlockPos start = base.add(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            for (int k = 0; k < 3; k++) {
                BlockPos column = start.offset(along, k);
                world.setBlockState(world.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, column), stone, 2);
            }
        }
        // Кристаллы
        for (int i = 0; i < 5; i++) {
            BlockPos column = base.add(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
            BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, column);
            if (world.getBlockState(top).isAir()) {
                world.setBlockState(top, ModBlocks.GLOWING_CRYSTAL.getDefaultState().with(AmethystClusterBlock.FACING, Direction.UP), 2);
            }
        }
        // Постамент и планшет
        BlockPos plinth = world.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, base);
        world.setBlockState(plinth, stone, 2);
        world.setBlockState(plinth.up(), ModBlocks.HOLO_TABLET.getDefaultState()
                .with(HoloTabletBlock.FACING, Direction.Type.HORIZONTAL.random(random)), 2);
        world.setBlockState(plinth.up(2), Blocks.AIR.getDefaultState(), 2);
        if (world.getBlockEntity(plinth.up()) instanceof HoloTabletBlockEntity tablet) {
            tablet.setLog(log);
        }
    }
}
