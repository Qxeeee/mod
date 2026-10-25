package com.orbitcore.story;

import com.orbitcore.block.HoloTabletBlock;
import com.orbitcore.block.entity.HoloTabletBlockEntity;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.world.EchoSite;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Голографические планшеты и сканер сигналов. Пока запись не прочитана, планшет
 * источает сигнал: сканер в руке слышит его и показывает силу, а если постоять с ним
 * неподвижно, расшифровывает направление. Прочитанная запись больше не зовёт.
 */
public final class HoloTablets {
    /** Записи «Зари»: в модуле, у лунохода, где обрываются следы, у Эха. */
    public static final List<String> ZARYA_LOGS = List.of("zarya_1", "zarya_2", "zarya_3", "zarya_4");
    /** Сколько разных записей Строителей разбросано по руинам. */
    public static final int BUILDER_LOGS = 8;
    /** Дальше этого сканер сигнал не слышит. */
    public static final int SCANNER_RANGE = 600;
    /** Сколько тиков держится расшифрованный пеленг. */
    private static final int FIX_TICKS = 20 * 60;
    private static final Map<UUID, Fix> FIXES = new HashMap<>();

    private record Fix(BlockPos pos, long until) {
    }

    private HoloTablets() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(HoloTablets::tick);
    }

    // ------------------------------------------------------------------ чтение

    /** Игрок открыл планшет: запись расшифрована, сигнал планшета иссяк. */
    public static void read(ServerPlayerEntity player, BlockPos pos, HoloTabletBlockEntity tablet) {
        String log = tablet.log();
        if (log.isEmpty()) {
            player.sendMessage(Text.translatable("message.orbitcore.holo.blank").formatted(Formatting.GRAY), true);
            return;
        }
        ServerWorld world = player.getServerWorld();
        MinecraftServer server = world.getServer();
        SpaceProgram program = SpaceProgram.get(server);
        if (program.readLog(server, log)) {
            program.addPoints(server, ScienceType.OBSERVATION, 6);
            player.sendMessage(Text.translatable("message.orbitcore.holo.decoded", Text.translatable(titleKey(log)))
                    .formatted(Formatting.AQUA), false);
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof HoloTabletBlock && state.get(HoloTabletBlock.ACTIVE)) {
            world.setBlockState(pos, state.with(HoloTabletBlock.ACTIVE, false));
            world.playSound(null, pos, ModSounds.ECHO_STATIC, SoundCategory.BLOCKS, 0.6f, 1.3f);
            if (world.getRegistryKey() == ModDimensions.MOON) {
                EchoSite site = EchoSite.get(world);
                site.drain(pos.equals(site.tablets().get(log)) ? log : drainKey(pos, log));
            }
        }
        OrbitNetworking.sendHoloLog(player, log);
    }

    public static String titleKey(String log) {
        return "holo.orbitcore." + log + ".title";
    }

    /** Записи сюжета иссякают по имени, руины — по своей клетке. */
    private static String drainKey(BlockPos pos, String log) {
        return log.startsWith("builder_") ? RuinLayout.key(RuinLayout.cell(pos.getX()), RuinLayout.cell(pos.getZ())) : log;
    }

    // ------------------------------------------------------------------ сканер

    /** Все ещё живые сигналы вокруг: планшеты сюжета и руины, которые никто не прочитал. */
    public static List<BlockPos> signals(ServerWorld moon, BlockPos from) {
        EchoSite site = EchoSite.get(moon);
        List<BlockPos> signals = new ArrayList<>();
        site.tablets().forEach((log, pos) -> {
            if (!site.drained(log)) {
                signals.add(pos);
            }
        });
        for (RuinLayout.Ruin ruin : RuinLayout.near(moon.getSeed(), from, SCANNER_RANGE)) {
            if (!site.drained(ruin.key())) {
                signals.add(ruin.center());
            }
        }
        return signals;
    }

    @Nullable
    public static BlockPos nearest(ServerWorld moon, BlockPos from) {
        BlockPos best = null;
        double bestDistance = (double) SCANNER_RANGE * SCANNER_RANGE;
        for (BlockPos pos : signals(moon, from)) {
            double dx = pos.getX() - from.getX();
            double dz = pos.getZ() - from.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = pos;
            }
        }
        return best;
    }

    /** Сканер продержали неподвижно до конца: пеленг на ближайший сигнал. */
    @Nullable
    public static BlockPos decode(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (world.getRegistryKey() != ModDimensions.MOON) {
            player.sendMessage(Text.translatable("message.orbitcore.scanner.no_signal").formatted(Formatting.GRAY), true);
            return null;
        }
        BlockPos target = nearest(world, player.getBlockPos());
        if (target == null) {
            player.sendMessage(Text.translatable("message.orbitcore.scanner.no_signal").formatted(Formatting.GRAY), true);
            return null;
        }
        FIXES.put(player.getUuid(), new Fix(target, world.getTime() + FIX_TICKS));
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        player.sendMessage(Text.translatable("message.orbitcore.scanner.decoded", Math.round(Math.sqrt(dx * dx + dz * dz)))
                .formatted(Formatting.AQUA), true);
        world.playSound(null, player.getBlockPos(), ModSounds.ECHO_HOLOGRAM, SoundCategory.PLAYERS, 0.6f, 1.4f);
        return target;
    }

    private static void tick(ServerWorld world) {
        if (world.getRegistryKey() != ModDimensions.MOON || world.getTime() % 10 != 0) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.getMainHandStack().isOf(ModItems.SIGNAL_SCANNER) && !player.getOffHandStack().isOf(ModItems.SIGNAL_SCANNER)) {
                continue;
            }
            BlockPos nearest = nearest(world, player.getBlockPos());
            float distance = -1;
            if (nearest != null) {
                double dx = nearest.getX() - player.getX();
                double dz = nearest.getZ() - player.getZ();
                distance = (float) Math.sqrt(dx * dx + dz * dz);
            }
            Fix fix = FIXES.get(player.getUuid());
            BlockPos fixed = null;
            if (fix != null && fix.until > world.getTime() && signals(world, fix.pos).contains(fix.pos)) {
                fixed = fix.pos;
            } else {
                FIXES.remove(player.getUuid());
            }
            OrbitNetworking.sendScanner(player, distance, fixed);
        }
    }
}
