package com.orbitcore.world;

import com.orbitcore.entity.WatcherEntity;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.science.CosmopediaEntry;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.List;

/**
 * Акт I сюжета «Эхо» на Луне: после первой посадки рядом находится брошенная «Заря»,
 * через минуту ЦУП ловит сигнал, у кратера Эха игроку становится по-настоящему
 * не по себе, а касание монолита запускает катсцену и даёт Осколок Эха.
 */
public final class EchoEvents {
    /** Через сколько тиков после первой посадки приходит сигнал. */
    public static final int SIGNAL_DELAY = 20 * 60;
    /** На каком расстоянии от модуля игрок «находит» «Зарю». */
    public static final int ZARYA_RADIUS = 10;
    /** На каком расстоянии от Эха случается страшный момент. */
    public static final int SCARE_RADIUS = 64;
    /** Эхо строится, когда кто-то подходит ближе, чем на столько блоков. */
    public static final int BUILD_RADIUS = 320;
    /** «Заря» стоит в стольких блоках от места первой посадки, по пути к Эху. */
    private static final int ZARYA_DISTANCE = 70;

    private EchoEvents() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(EchoEvents::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSite(handler.player, false));
    }

    // ------------------------------------------------------------------ посадка и сигнал

    /** Первая посадка на Луну: рядом появляется «Заря», запускается отсчёт до сигнала. */
    public static void onMoonLanding(ServerWorld moon, BlockPos landing) {
        EchoSite site = EchoSite.get(moon);
        if (site.zarya() == null) {
            BlockPos echo = site.echo(moon);
            Vec3d way = new Vec3d(echo.getX() - landing.getX(), 0, echo.getZ() - landing.getZ());
            Vec3d unit = way.lengthSquared() < 1 ? new Vec3d(1, 0, 0) : way.normalize();
            BlockPos near = BlockPos.ofFloored(landing.getX() + unit.x * ZARYA_DISTANCE, landing.getY(), landing.getZ() + unit.z * ZARYA_DISTANCE);
            Direction toward = Direction.getFacing(unit.x, 0, unit.z);
            site.setZarya(EchoStructures.buildZarya(moon, near, toward, moon.random));
        }
        if (site.signalCountdown() < 0 && !SpaceProgram.get(moon.getServer()).hasFirst("echo_signal")) {
            site.setSignalCountdown(SIGNAL_DELAY);
        }
    }

    private static void tick(ServerWorld world) {
        if (world.getRegistryKey() != ModDimensions.MOON) {
            return;
        }
        MinecraftServer server = world.getServer();
        SpaceProgram program = SpaceProgram.get(server);
        EchoSite site = EchoSite.get(world);
        List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator());
        // Мир, где на Луну садились ещё до этой версии: «Заря» появится рядом с первым же игроком
        if (site.zarya() == null && program.hasFirst("moon_landing") && !players.isEmpty() && world.getTime() % 100 == 0) {
            onMoonLanding(world, players.get(0).getBlockPos());
        }
        int countdown = site.signalCountdown();
        if (countdown > 0) {
            site.setSignalCountdown(countdown - 1);
            if (countdown == 1) {
                receiveSignal(server, world, site);
            }
        }
        if (world.getTime() % 20 != 0) {
            return;
        }
        BlockPos echo = site.echo(world);
        for (ServerPlayerEntity player : players) {
            double toEcho = horizontalDistance(player, echo);
            if (!site.echoBuilt() && toEcho < BUILD_RADIUS) {
                site.setEcho(EchoStructures.buildEcho(world, echo, world.random));
            }
            if (site.echoBuilt() && toEcho < SCARE_RADIUS && site.markScared(player.getUuid())) {
                scare(world, player);
            }
            BlockPos zarya = site.zarya();
            if (zarya != null && horizontalDistance(player, zarya) < ZARYA_RADIUS && program.markFirst(server, "zarya_found")) {
                program.addEntry(server, CosmopediaEntry.ZARYA.id());
                server.getPlayerManager().broadcast(Text.translatable("message.orbitcore.echo.zarya_found", player.getDisplayName())
                        .formatted(Formatting.GOLD), false);
            }
        }
    }

    /** ЦУП ловит сигнал: всем — отметка и пеленг, тем, кто на Луне, — расстояние и азимут. */
    private static void receiveSignal(MinecraftServer server, ServerWorld moon, EchoSite site) {
        site.setSignalCountdown(-1);
        SpaceProgram.get(server).markFirst(server, "echo_signal");
        BlockPos echo = site.echo(moon);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendSite(player, true);
            Text message = player.getServerWorld() == moon
                    ? Text.translatable("message.orbitcore.echo.signal", Math.round(horizontalDistance(player, echo)), azimuth(player, echo))
                    : Text.translatable("message.orbitcore.echo.signal_far");
            player.sendMessage(message.copy().formatted(Formatting.LIGHT_PURPLE), false);
        }
    }

    /** Отправить игроку, где Эхо и «Заря», если сигнал уже принят. */
    public static void sendSite(ServerPlayerEntity player, boolean announce) {
        MinecraftServer server = player.getServer();
        if (server == null || !SpaceProgram.get(server).hasFirst("echo_signal")) {
            return;
        }
        ServerWorld moon = server.getWorld(ModDimensions.MOON);
        if (moon == null) {
            return;
        }
        EchoSite site = EchoSite.get(moon);
        OrbitNetworking.sendEchoSite(player, site.echo(moon), site.zarya(), announce);
    }

    // ------------------------------------------------------------------ страшный момент

    /**
     * У края кратера: удар по нервам, помехи по всему визору, а за спиной, на гребне,
     * стоит кто-то в скафандре «Зари». Стоит обернуться — его уже нет.
     */
    public static void scare(ServerWorld world, ServerPlayerEntity player) {
        OrbitNetworking.sendScare(player);
        WatcherEntity watcher = ModEntities.WATCHER.create(world);
        if (watcher == null) {
            return;
        }
        Vec3d back = player.getRotationVec(1.0f).multiply(-1, 0, -1);
        back = back.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : back.normalize();
        Vec3d spot = player.getPos().add(back.multiply(24));
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(spot));
        watcher.refreshPositionAndAngles(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0, 0);
        watcher.watch(player);
        world.spawnEntity(watcher);
    }

    // ------------------------------------------------------------------ контакт

    /** Игрок коснулся монолита. В первый раз — катсцена и Осколок Эха, потом Эхо молчит. */
    public static void touch(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        ServerWorld moon = server == null ? null : server.getWorld(ModDimensions.MOON);
        if (moon == null) {
            return;
        }
        EchoSite site = EchoSite.get(moon);
        if (!site.markContact(player.getUuid())) {
            player.sendMessage(Text.translatable("message.orbitcore.echo.silent").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), true);
            player.getServerWorld().playSound(null, player.getBlockPos(), ModSounds.ECHO_STATIC, SoundCategory.AMBIENT, 0.5f, 0.6f);
            return;
        }
        SpaceProgram program = SpaceProgram.get(server);
        if (program.markFirst(server, "echo_contact")) {
            server.getPlayerManager().broadcast(Text.translatable("message.orbitcore.echo.first_contact", player.getDisplayName())
                    .formatted(Formatting.GOLD), false);
        }
        program.addEntry(server, CosmopediaEntry.ECHO.id());
        program.addPoints(server, ScienceType.OBSERVATION, 25);
        program.addPoints(server, ScienceType.SAMPLE, 25);
        ItemStack shard = new ItemStack(ModItems.ECHO_SHARD);
        if (!player.getInventory().insertStack(shard)) {
            player.dropItem(shard, false);
        }
        OrbitNetworking.sendEchoContact(player, site.echo(moon));
    }

    // ------------------------------------------------------------------ помощники

    public static double horizontalDistance(ServerPlayerEntity player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Азимут на точку: 0° — север, 90° — восток. */
    public static int azimuth(ServerPlayerEntity player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dx, -dz))), 360);
    }
}
