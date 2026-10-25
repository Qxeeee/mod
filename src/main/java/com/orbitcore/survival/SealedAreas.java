package com.orbitcore.survival;

import com.orbitcore.block.AirlockDoorBlock;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Герметичные объёмы, которые держат герметизаторы. Воздух внутри можно
 * дышать даже на Луне. Объём ищется заливкой от герметизатора: воздух проходит
 * через всё, кроме цельных блоков и закрытых шлюзовых дверей.
 */
public final class SealedAreas {
    private static final Map<RegistryKey<World>, Map<BlockPos, LongSet>> AREAS = new HashMap<>();

    private SealedAreas() {
    }

    /**
     * Итог заливки: замкнутый объём или место, где воздух уходит наружу.
     *
     * @param cells клетки с воздухом, если объём замкнут
     * @param leak  самое узкое место на пути воздуха наружу (дыра в стене), если не замкнут
     * @param leakDirection куда дует воздух из дыры
     */
    public record Scan(@Nullable LongSet cells, @Nullable BlockPos leak, @Nullable Direction leakDirection) {
        public boolean sealed() {
            return cells != null;
        }

        public int volume() {
            return cells == null ? 0 : cells.size();
        }
    }

    /** Пропускает ли блок воздух. */
    public static boolean isAirtight(World world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof AirlockDoorBlock) {
            return !state.get(AirlockDoorBlock.OPEN);
        }
        return state.isFullCube(world, pos);
    }

    /**
     * Заливка воздухом из решётки блока {@code source} на стороне {@code outlet}
     * (сам блок в объём не входит).
     */
    public static Scan scan(World world, BlockPos source, Direction outlet, int maxVolume) {
        LongOpenHashSet visited = new LongOpenHashSet();
        // Для каждой клетки — откуда в неё пришёл воздух: по этой цепочке ищем дыру
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        long sourceKey = source.asLong();
        pos.set(source, outlet);
        if (!open(world, pos, visited)) {
            return new Scan(null, null, null);
        }
        visited.add(pos.asLong());
        parent.put(pos.asLong(), sourceKey);
        queue.enqueue(pos.asLong());
        long escaped = Long.MIN_VALUE;
        while (!queue.isEmpty()) {
            long cell = queue.dequeueLong();
            if (visited.size() > maxVolume || !loaded(world, BlockPos.fromLong(cell))) {
                escaped = cell;
                break;
            }
            for (Direction direction : Direction.values()) {
                pos.set(BlockPos.fromLong(cell), direction);
                if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) {
                    // Воздух уходит за пределы мира
                    escaped = cell;
                    break;
                }
                if (open(world, pos, visited)) {
                    long next = pos.asLong();
                    visited.add(next);
                    parent.put(next, cell);
                    queue.enqueue(next);
                }
            }
            if (escaped != Long.MIN_VALUE) {
                break;
            }
        }
        if (escaped == Long.MIN_VALUE) {
            return new Scan(visited, null, null);
        }
        return findLeak(world, parent, sourceKey, escaped);
    }

    /**
     * Идём от точки, куда ушёл воздух, обратно к герметизатору и ищем самое узкое
     * место — клетку, у которой больше всего глухих соседей. Это и есть дыра.
     */
    private static Scan findLeak(World world, Long2LongOpenHashMap parent, long sourceKey, long escaped) {
        BlockPos best = null;
        Direction bestDirection = null;
        int bestScore = -1;
        long previous = Long.MIN_VALUE;
        long cell = escaped;
        while (cell != sourceKey && parent.containsKey(cell)) {
            BlockPos pos = BlockPos.fromLong(cell);
            int score = 0;
            for (Direction direction : Direction.values()) {
                BlockPos side = pos.offset(direction);
                if (loaded(world, side) && isAirtight(world, side, world.getBlockState(side))) {
                    score++;
                }
            }
            // При равенстве остаётся клетка ближе к выходу: проём в стене, а не угол комнаты
            if (score > bestScore) {
                bestScore = score;
                best = pos;
                bestDirection = previous == Long.MIN_VALUE ? Direction.UP : direction(pos, BlockPos.fromLong(previous));
            }
            previous = cell;
            cell = parent.get(cell);
        }
        return new Scan(null, bestScore >= 3 ? best : null, bestDirection);
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        return Direction.fromVector(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    private static boolean open(World world, BlockPos pos, LongSet visited) {
        if (visited.contains(pos.asLong())) {
            return false;
        }
        if (!loaded(world, pos)) {
            return true;
        }
        return !isAirtight(world, pos, world.getBlockState(pos));
    }

    private static boolean loaded(World world, BlockPos pos) {
        return world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }

    public static void put(World world, BlockPos sealer, LongSet cells) {
        AREAS.computeIfAbsent(world.getRegistryKey(), key -> new HashMap<>()).put(sealer.toImmutable(), cells);
    }

    public static void remove(World world, BlockPos sealer) {
        Map<BlockPos, LongSet> areas = AREAS.get(world.getRegistryKey());
        if (areas != null) {
            areas.remove(sealer);
        }
    }

    /** Есть ли в этой клетке воздух от работающего герметизатора. */
    public static boolean isSealed(World world, BlockPos pos) {
        Map<BlockPos, LongSet> areas = AREAS.get(world.getRegistryKey());
        if (areas == null || areas.isEmpty()) {
            return false;
        }
        long key = pos.asLong();
        for (LongSet cells : areas.values()) {
            if (cells.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        AREAS.clear();
    }
}
