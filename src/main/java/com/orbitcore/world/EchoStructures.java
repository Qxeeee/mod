package com.orbitcore.world;

import com.orbitcore.block.RegolithBlock;
import com.orbitcore.entity.RoverEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModItems;
import com.orbitcore.survival.Oxygen;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Постройки сюжета «Эхо» на Луне. Строятся кодом из обычных блоков, чтобы их было
 * легко поправить без редактора структур.
 */
public final class EchoStructures {
    /** Флаги установки: обновить клиентов, но не трогать соседей (лестница и дверь ставятся раньше опоры). */
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    /** Радиус кратера «Эха». */
    public static final int ECHO_CRATER = 22;
    /** Сколько страниц в бортжурнале «Зари». */
    public static final int JOURNAL_PAGES = 5;

    private EchoStructures() {
    }

    // ------------------------------------------------------------------ «Заря»

    /**
     * Брошенный посадочный модуль «Зари». Дверь открыта, на пюпитре бортжурнал.
     * От модуля к Эху уходят следы лунохода; сам луноход стоит на полпути с севшей
     * батареей, а дальше идут только следы ног — и обрываются.
     *
     * @param toward в какую сторону уехала экспедиция (к Эху)
     * @return центр модуля на уровне грунта
     */
    public static BlockPos buildZarya(ServerWorld world, BlockPos near, Direction toward, Random random) {
        BlockRotation rotation = rotationTo(toward);
        loadChunks(world, near, 72);
        BlockPos base = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, near);
        // Площадка: под модулем ровный грунт, над ним пусто
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -3; dy < 0; dy++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, ModBlocks.REGOLITH.getDefaultState(), FLAGS);
                    }
                }
                for (int dy = 0; dy < 12; dy++) {
                    world.setBlockState(base.add(dx, dy, dz), Blocks.AIR.getDefaultState(), FLAGS);
                }
            }
        }
        Placer p = new Placer(world, base, rotation);
        // Опоры
        for (int x : new int[]{-3, 3}) {
            for (int z : new int[]{-3, 3}) {
                for (int y = 0; y <= 2; y++) {
                    p.put(x, y, z, Blocks.IRON_BARS.getDefaultState());
                }
            }
        }
        // Посадочная ступень в золотой фольге
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                for (int y = 2; y <= 3; y++) {
                    p.put(x, y, z, corner ? Blocks.IRON_BLOCK.getDefaultState() : Blocks.YELLOW_CONCRETE.getDefaultState());
                }
            }
        }
        // Кабина 5×5, внутри 3×3×3
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                for (int y = 4; y <= 6; y++) {
                    p.put(x, y, z, wall ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.AIR.getDefaultState());
                }
                p.put(x, 7, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
            }
        }
        p.put(0, 7, 0, Blocks.REDSTONE_LAMP.getDefaultState());
        p.put(-2, 5, 0, Blocks.GLASS_PANE.getDefaultState());
        p.put(2, 5, 0, Blocks.GLASS_PANE.getDefaultState());
        // Открытая дверь шлюза спереди и лестница к ней
        BlockState door = ModBlocks.AIRLOCK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.OPEN, true);
        p.put(0, 4, -2, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        p.put(0, 5, -2, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        p.put(0, 0, -2, ModBlocks.STEEL_BLOCK.getDefaultState());
        p.put(0, 1, -2, ModBlocks.STEEL_BLOCK.getDefaultState());
        for (int y = 0; y <= 3; y++) {
            p.put(0, y, -3, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
        }
        // Внутри: пюпитр с бортжурналом и ящик с остатками снаряжения
        p.put(0, 4, 1, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.NORTH).with(LecternBlock.HAS_BOOK, true));
        if (world.getBlockEntity(p.at(0, 4, 1)) instanceof LecternBlockEntity lectern) {
            lectern.setBook(zaryaLogbook());
        }
        p.put(-1, 4, 1, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
        if (world.getBlockEntity(p.at(-1, 4, 1)) instanceof ChestBlockEntity chest) {
            ItemStack tank = new ItemStack(ModItems.OXYGEN_TANK);
            Oxygen.set(tank, Oxygen.capacity(tank) / 6);
            chest.setStack(0, tank);
            chest.setStack(1, new ItemStack(ModItems.DOSIMETER));
            chest.setStack(4, new ItemStack(Items.IRON_NUGGET, 7));
            chest.setStack(9, new ItemStack(Items.BREAD, 3));
            chest.setStack(13, new ItemStack(ModItems.CRYSTAL_SHARD, 2));
        }
        // Антенна на крыше
        p.put(1, 8, 1, Blocks.LIGHTNING_ROD.getDefaultState());
        p.put(-1, 8, 1, Blocks.IRON_BARS.getDefaultState());
        p.put(-1, 9, 1, Blocks.DAYLIGHT_DETECTOR.getDefaultState());
        // Флаг США: синий крыж у древка и красно-белые полосы
        for (int y = 0; y <= 5; y++) {
            p.put(4, y, -5, Blocks.IRON_BARS.getDefaultState());
        }
        for (int x = 5; x <= 9; x++) {
            for (int y = 2; y <= 5; y++) {
                boolean canton = x <= 6 && y >= 4;
                BlockState wool = canton ? Blocks.BLUE_WOOL.getDefaultState()
                        : (y % 2 == 1 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).getDefaultState();
                p.put(x, y, -5, wool);
            }
        }

        // Колея лунохода к Эху, сам луноход с пустой батареей, дальше — шаги, которые обрываются
        for (int i = 6; i <= 36; i++) {
            trodden(world, p.at(-1, 0, -i));
            trodden(world, p.at(1, 0, -i));
        }
        RoverEntity rover = ModEntities.ROVER.create(world);
        if (rover != null) {
            BlockPos at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p.at(0, 0, -39));
            rover.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, toward.asRotation(), 0);
            rover.setBattery(0);
            world.spawnEntity(rover);
        }
        for (int i = 42; i <= 64; i++) {
            trodden(world, p.at(i % 2 == 0 ? 0 : 1, 0, -i));
        }
        return base;
    }

    /** Бортжурнал командира «Зари». Страницы переводятся, как и весь мод. */
    public static ItemStack zaryaLogbook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();
        nbt.putString("title", "Zarya");
        nbt.putString("author", "I. Volkova");
        NbtList pages = new NbtList();
        for (int i = 1; i <= JOURNAL_PAGES; i++) {
            pages.add(NbtString.of(Text.Serializer.toJson(Text.translatable("journal.orbitcore.zarya." + i))));
        }
        nbt.put("pages", pages);
        book.setCustomName(Text.translatable("item.orbitcore.zarya_logbook"));
        return book;
    }

    private static void trodden(ServerWorld world, BlockPos column) {
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column).down();
        BlockState state = world.getBlockState(top);
        if (state.getBlock() instanceof RegolithBlock) {
            world.setBlockState(top, state.with(RegolithBlock.TRODDEN, true), FLAGS);
        }
    }

    // ------------------------------------------------------------------ «Эхо»

    /**
     * Объект «Эхо»: чёрный многогранный монолит из эхового камня в центре большого
     * кратера, вокруг — кольцо светящихся кристаллов и обломки того же камня.
     *
     * @return основание монолита (на дне кратера)
     */
    public static BlockPos buildEcho(ServerWorld world, BlockPos center, Random random) {
        loadChunks(world, center, (int) (ECHO_CRATER * 1.5) + 4);
        BlockPos floor = CraterFeature.blast(world, center, ECHO_CRATER, random);
        BlockState stone = ModBlocks.ECHO_STONE.getDefaultState();
        // Цоколь вровень с дном
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                    world.setBlockState(floor.add(dx, 0, dz), stone, FLAGS);
                }
            }
        }
        // Монолит: восьмигранник, сужается кверху, с двумя вдавленными поясами
        for (int y = 1; y <= 18; y++) {
            int r = y <= 12 ? 2 : y <= 15 ? 1 : 0;
            if (y == 5 || y == 10) {
                r = Math.max(0, r - 1);
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= r + (r > 1 ? 1 : 0)) {
                        world.setBlockState(floor.add(dx, y, dz), stone, FLAGS);
                    }
                }
            }
        }
        // Кольцо кристаллов
        for (int i = 0; i < 16; i++) {
            float angle = i / 16f * MathHelper.TAU;
            crystal(world, floor.add(Math.round(MathHelper.cos(angle) * 7), 0, Math.round(MathHelper.sin(angle) * 7)));
        }
        // Кристаллы по всему дну и обломки эхового камня
        for (int i = 0; i < 40; i++) {
            float angle = random.nextFloat() * MathHelper.TAU;
            float d = 5 + random.nextFloat() * (ECHO_CRATER - 6);
            crystal(world, floor.add(Math.round(MathHelper.cos(angle) * d), 0, Math.round(MathHelper.sin(angle) * d)));
        }
        for (int i = 0; i < 7; i++) {
            float angle = random.nextFloat() * MathHelper.TAU;
            float d = 9 + random.nextFloat() * 5;
            BlockPos column = floor.add(Math.round(MathHelper.cos(angle) * d), 0, Math.round(MathHelper.sin(angle) * d));
            BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            for (int y = 0; y <= random.nextInt(3); y++) {
                world.setBlockState(top.up(y), stone, FLAGS);
            }
        }
        return floor;
    }

    private static void crystal(ServerWorld world, BlockPos column) {
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
        if (world.getBlockState(top.down()).isSolidBlock(world, top.down()) && world.getBlockState(top).isAir()) {
            world.setBlockState(top, ModBlocks.GLOWING_CRYSTAL.getDefaultState().with(AmethystClusterBlock.FACING, Direction.UP), FLAGS);
        }
    }

    // ------------------------------------------------------------------ общее

    /** Постройка может стоять там, где игрока ещё не было: загрузить (и сгенерировать) нужные чанки. */
    private static void loadChunks(ServerWorld world, BlockPos center, int radius) {
        int minX = ChunkSectionPos.getSectionCoord(center.getX() - radius);
        int maxX = ChunkSectionPos.getSectionCoord(center.getX() + radius);
        int minZ = ChunkSectionPos.getSectionCoord(center.getZ() - radius);
        int maxZ = ChunkSectionPos.getSectionCoord(center.getZ() + radius);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getChunk(x, z);
            }
        }
    }

    /** Поворот, который переводит «перёд» постройки (север) в нужную сторону. */
    private static BlockRotation rotationTo(Direction toward) {
        return switch (toward) {
            case EAST -> BlockRotation.CLOCKWISE_90;
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** Ставит блоки в осях постройки: перёд — север (−Z), затем всё поворачивается. */
    private record Placer(ServerWorld world, BlockPos base, BlockRotation rotation) {
        BlockPos at(int x, int y, int z) {
            return base.add(new BlockPos(x, y, z).rotate(rotation));
        }

        void put(int x, int y, int z, BlockState state) {
            world.setBlockState(at(x, y, z), state.rotate(rotation), FLAGS);
        }
    }
}
