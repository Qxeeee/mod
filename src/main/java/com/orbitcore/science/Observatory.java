package com.orbitcore.science;

import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModBlocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Серверная логика телескопа: когда можно наблюдать и что даёт снимок.
 */
public final class Observatory {
    /** Как далеко игрок может отойти от телескопа во время наблюдения. */
    private static final double MAX_DISTANCE_SQ = 8 * 8;

    private Observatory() {
    }

    /** Причина, по которой сейчас нельзя наблюдать, или null, если можно. */
    @Nullable
    public static Text whyCannotObserve(World world, BlockPos telescope) {
        if (!world.getDimension().hasSkyLight() || world.getDimension().hasFixedTime()) {
            return Text.translatable("message.orbitcore.telescope.no_sky_here");
        }
        if (!ObservationTarget.isNight(world.getTimeOfDay())) {
            return Text.translatable("message.orbitcore.telescope.daytime");
        }
        if (world.isRaining()) {
            return Text.translatable("message.orbitcore.telescope.cloudy");
        }
        if (!world.isSkyVisible(telescope.up())) {
            return Text.translatable("message.orbitcore.telescope.blocked");
        }
        return null;
    }

    public static long day(World world) {
        return Math.floorDiv(world.getTimeOfDay(), 24000L);
    }

    /** Клиент сообщил, что снимок цели сделан. Проверяем и засчитываем. */
    public static void observe(ServerPlayerEntity player, BlockPos pos, String targetId) {
        ServerWorld world = player.getServerWorld();
        ObservationTarget target = ObservationTarget.byId(targetId);
        if (target == null
                || !world.getBlockState(pos).isOf(ModBlocks.TELESCOPE)
                || player.squaredDistanceTo(pos.toCenterPos()) > MAX_DISTANCE_SQ) {
            return;
        }
        Text problem = whyCannotObserve(world, pos);
        if (problem != null) {
            player.sendMessage(problem.copy().formatted(Formatting.RED), true);
            return;
        }
        SpaceProgram program = SpaceProgram.get(world.getServer());
        if (!program.recordObservation(world.getServer(), target, day(world))) {
            player.sendMessage(Text.translatable("message.orbitcore.telescope.already_observed",
                    Text.translatable(target.translationKey())).formatted(Formatting.GRAY), true);
            return;
        }
        player.sendMessage(Text.translatable("message.orbitcore.science_gained", target.points(),
                Text.translatable(ScienceType.OBSERVATION.translationKey())).formatted(Formatting.AQUA), true);

        CelestialBody body = target.discovers();
        if (body != null && program.discover(world.getServer(), body)) {
            world.getServer().getPlayerManager().broadcast(Text.translatable("message.orbitcore.discovery",
                    player.getDisplayName(), Text.translatable(body.translationKey())).formatted(Formatting.GOLD), false);
            OrbitNetworking.announceDiscovery(world.getServer(), body);
        }
    }
}
