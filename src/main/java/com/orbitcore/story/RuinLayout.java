package com.orbitcore.story;

import com.orbitcore.world.EchoSite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Где на Луне стоят руины Строителей. Луна поделена на клетки 400×400; в большинстве
 * клеток есть одни руины, их место зависит только от сида. Поэтому сканер знает, куда
 * вести игрока, даже если те места ещё ни разу не загружались.
 */
public final class RuinLayout {
    public static final int CELL = 400;
    private static final double CHANCE = 0.7;
    /** У самого Эха руин нет: там свой кратер. */
    private static final int ECHO_CLEARANCE = 150;

    private RuinLayout() {
    }

    public record Ruin(int cellX, int cellZ, BlockPos center) {
        public String key() {
            return RuinLayout.key(cellX, cellZ);
        }
    }

    public static int cell(int coordinate) {
        return Math.floorDiv(coordinate, CELL);
    }

    public static String key(int cellX, int cellZ) {
        return "ruin:" + cellX + ":" + cellZ;
    }

    private static Random random(long seed, int cellX, int cellZ) {
        return Random.create(seed ^ cellX * 341873128712L ^ cellZ * 132897987541L ^ 0x52554E53L);
    }

    /** Центр руин в клетке (y = 0) или null, если в этой клетке их нет. */
    @Nullable
    public static BlockPos center(long seed, int cellX, int cellZ) {
        Random random = random(seed, cellX, cellZ);
        if (random.nextDouble() > CHANCE) {
            return null;
        }
        int x = cellX * CELL + 60 + random.nextInt(CELL - 120);
        int z = cellZ * CELL + 60 + random.nextInt(CELL - 120);
        BlockPos echo = EchoSite.echoColumn(seed);
        double dx = x - echo.getX();
        double dz = z - echo.getZ();
        if (dx * dx + dz * dz < ECHO_CLEARANCE * ECHO_CLEARANCE) {
            return null;
        }
        return new BlockPos(x, 0, z);
    }

    /** Какая запись Строителей лежит в этих руинах. */
    public static String log(long seed, int cellX, int cellZ) {
        Random random = random(seed, cellX, cellZ);
        random.nextDouble();
        return "builder_" + (1 + random.nextInt(HoloTablets.BUILDER_LOGS));
    }

    /** Руины в радиусе {@code range} блоков. */
    public static List<Ruin> near(long seed, BlockPos from, int range) {
        List<Ruin> ruins = new ArrayList<>();
        for (int cx = cell(from.getX() - range); cx <= cell(from.getX() + range); cx++) {
            for (int cz = cell(from.getZ() - range); cz <= cell(from.getZ() + range); cz++) {
                BlockPos center = center(seed, cx, cz);
                if (center != null) {
                    ruins.add(new Ruin(cx, cz, center));
                }
            }
        }
        return ruins;
    }
}
