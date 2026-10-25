package com.orbitcore.gametest;

import com.orbitcore.entity.RoverEntity;
import com.orbitcore.entity.WatcherEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModItems;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.world.EchoEvents;
import com.orbitcore.world.EchoSite;
import com.orbitcore.world.EchoStructures;
import com.orbitcore.world.ModDimensions;
import com.orbitcore.block.RegolithBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Этап 0.8, часть 2: «Заря», объект «Эхо», сигнал, луноход, Наблюдатель.
 */
public class EchoGameTest implements FabricGameTest {
    private static ServerWorld moon(TestContext context) {
        ServerWorld moon = context.getWorld().getServer().getWorld(ModDimensions.MOON);
        context.assertTrue(moon != null, "the Moon dimension must exist");
        return moon;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void zaryaHasLogbookDeadRoverAndTracks(TestContext context) {
        ServerWorld moon = moon(context);
        BlockPos base = EchoStructures.buildZarya(moon, new BlockPos(6000, 80, 6000), Direction.NORTH, moon.random);
        boolean logbook = false;
        for (BlockPos pos : BlockPos.iterate(base.add(-2, 3, -2), base.add(2, 6, 2))) {
            if (moon.getBlockEntity(pos) instanceof LecternBlockEntity lectern && lectern.hasBook()) {
                logbook = true;
            }
        }
        context.assertTrue(logbook, "the Zarya logbook must lie on the lectern");
        List<RoverEntity> rovers = moon.getEntitiesByType(ModEntities.ROVER, new Box(base).expand(6, 8, 50), r -> true);
        context.assertTrue(rovers.size() == 1, "the abandoned rover must stand on the way, found " + rovers.size());
        context.assertTrue(rovers.get(0).battery() == 0, "the abandoned rover battery must be dead");
        int tracks = 0;
        for (BlockPos pos : BlockPos.iterate(base.add(-2, -6, -66), base.add(2, 6, -6))) {
            BlockState state = moon.getBlockState(pos);
            if (state.getBlock() instanceof RegolithBlock && state.get(RegolithBlock.TRODDEN)) {
                tracks++;
            }
        }
        context.assertTrue(tracks > 40, "rover tracks and footprints must lead away, found " + tracks);
        rovers.forEach(RoverEntity::discard);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void echoMonolithStandsInCraterAndCannotBeBroken(TestContext context) {
        ServerWorld moon = moon(context);
        BlockPos floor = EchoStructures.buildEcho(moon, new BlockPos(-7000, 0, 7000), moon.random);
        int stone = 0;
        for (int y = 1; y <= 18; y++) {
            if (moon.getBlockState(floor.up(y)).isOf(ModBlocks.ECHO_STONE)) {
                stone++;
            }
        }
        context.assertTrue(stone == 18, "the monolith must rise 18 blocks above the crater floor, got " + stone);
        BlockState state = moon.getBlockState(floor.up(3));
        context.assertTrue(state.getHardness(moon, floor.up(3)) < 0, "echo stone must be unbreakable");
        int crystals = 0;
        for (BlockPos pos : BlockPos.iterate(floor.add(-20, -2, -20), floor.add(20, 8, 20))) {
            if (moon.getBlockState(pos).isOf(ModBlocks.GLOWING_CRYSTAL)) {
                crystals++;
            }
        }
        context.assertTrue(crystals >= 12, "glowing crystals must ring the monolith, found " + crystals);
        context.complete();
    }

    // Отдельная партия: награда добавляет очки науки и не должна мешать тестам, которые их считают
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "echo_contact")
    public void touchingTheEchoGivesOneShard(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        EchoEvents.touch(player);
        EchoEvents.touch(player);
        int shards = player.getInventory().count(ModItems.ECHO_SHARD);
        context.assertTrue(shards == 1, "the Echo gives exactly one shard, got " + shards);
        SpaceProgram program = SpaceProgram.get(server);
        context.assertTrue(program.hasFirst("echo_contact"), "the first contact must be recorded");
        context.assertTrue(program.hasEntry("echo"), "the Cosmopedia must get the Echo entry");
        server.getPlayerManager().remove(player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void signalArrivesAfterLanding(TestContext context) {
        ServerWorld moon = moon(context);
        MinecraftServer server = moon.getServer();
        EchoSite site = EchoSite.get(moon);
        if (!SpaceProgram.get(server).hasFirst("echo_signal")) {
            site.setSignalCountdown(5);
        }
        context.addInstantFinalTask(() -> {
            context.assertTrue(SpaceProgram.get(server).hasFirst("echo_signal"), "Mission Control must receive the signal");
            context.assertTrue(site.signalCountdown() < 0, "the countdown must stop after the signal");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void roverNeedsChargeToDrive(TestContext context) {
        for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 1, 0), new BlockPos(7, 1, 7))) {
            context.setBlockState(pos, Blocks.STONE);
        }
        RoverEntity rover = context.spawnEntity(ModEntities.ROVER, new Vec3d(4, 2, 1));
        rover.setYaw(0);
        rover.tick();
        context.assertTrue(rover.battery() > 99, "a new rover must not lose charge on its first tick, got " + rover.battery());
        rover.setBattery(0);
        Vec3d start = rover.getPos();
        for (int i = 0; i < 30; i++) {
            rover.drive(1, 0);
        }
        context.assertTrue(rover.getPos().distanceTo(start) < 0.2, "a rover with a dead battery must not move");
        rover.setBattery(100);
        for (int i = 0; i < 40; i++) {
            rover.drive(1, 0);
            rover.tick();
        }
        double moved = horizontal(rover.getPos(), start);
        context.assertTrue(moved > 2.5, "a charged rover must drive forward, moved " + moved);
        context.assertTrue(rover.battery() < 100, "driving must drain the battery");
        rover.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void watcherVanishesWhenSeen(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Vec3d eye = context.getAbsolute(new Vec3d(1.5, 2, 1.5));
        player.refreshPositionAndAngles(eye.x, eye.y, eye.z, 0, 0);
        WatcherEntity watcher = context.spawnEntity(ModEntities.WATCHER, new Vec3d(1.5, 2, 14.5));
        watcher.watch(player);
        // Игрок смотрит прямо на Наблюдателя (на юг, вдоль +Z)
        Vec3d to = watcher.getEyePos().subtract(player.getEyePos());
        float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        float pitch = (float) -(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN);
        player.refreshPositionAndAngles(eye.x, eye.y, eye.z, yaw, pitch);
        context.addInstantFinalTask(() -> {
            context.assertTrue(watcher.isRemoved(), "the watcher must vanish once the player looks at him");
            context.getWorld().getServer().getPlayerManager().remove(player);
        });
    }

    private static double horizontal(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
