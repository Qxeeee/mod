package com.orbitcore.gametest;

import com.orbitcore.block.RegolithBlock;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModGameRules;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketDesign.Destination;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.List;

/**
 * Этап 0.6: мир Луны, гравитация, реголит, полёт к Луне и обратно.
 */
public class MoonGameTest implements FabricGameTest {
    private static ServerWorld moon(TestContext context) {
        ServerWorld moon = context.getWorld().getServer().getWorld(ModDimensions.MOON);
        context.assertTrue(moon != null, "the Moon dimension must exist");
        return moon;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void moonTerrainIsLunar(TestContext context) {
        ServerWorld moon = moon(context);
        int found = 0;
        for (int x = 0; x < 64; x += 16) {
            for (int z = 0; z < 64; z += 16) {
                moon.getChunk(x >> 4, z >> 4);
                int top = moon.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                BlockState surface = moon.getBlockState(new BlockPos(x, top - 1, z));
                context.assertTrue(top > 0 && top < 160, "surface height out of range: " + top);
                if (surface.isOf(ModBlocks.REGOLITH) || surface.isOf(ModBlocks.MARIA_REGOLITH) || surface.isOf(ModBlocks.ICE_REGOLITH)
                        || surface.isOf(ModBlocks.ANORTHOSITE) || surface.isOf(ModBlocks.MOON_BASALT)) {
                    found++;
                }
            }
        }
        context.assertTrue(found >= 14, "the Moon surface must be lunar rock and dust, found " + found + "/16");
        context.assertTrue(!ModDimensions.hasAir(moon), "there is no air on the Moon");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void moonGravityIsWeaker(TestContext context) {
        ServerWorld moon = moon(context);
        moon.getChunk(0, 0);
        int ground = moon.getTopY(Heightmap.Type.MOTION_BLOCKING, 8, 8);
        ArmorStandEntity lunar = EntityType.ARMOR_STAND.create(moon);
        lunar.refreshPositionAndAngles(8.5, ground + 40, 8.5, 0, 0);
        ArmorStandEntity earthly = EntityType.ARMOR_STAND.create(context.getWorld());
        Vec3d start = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(3, 1, 3))).add(0, 40, 0);
        earthly.refreshPositionAndAngles(start.x, start.y, start.z, 0, 0);
        double lunarStart = lunar.getY();
        // Одинаковые 30 шагов физики движения для обеих стоек
        for (int i = 0; i < 30; i++) {
            lunar.travel(Vec3d.ZERO);
            earthly.travel(Vec3d.ZERO);
        }
        double lunarDrop = lunarStart - lunar.getY();
        double earthDrop = start.y - earthly.getY();
        context.assertTrue(earthDrop > 5, "the Earth stand did not fall: " + earthDrop);
        context.assertTrue(lunarDrop > 0.3 && lunarDrop < earthDrop * 0.3,
                "lunar fall " + lunarDrop + " must be much shorter than " + earthDrop);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void regolithKeepsFootprints(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.REGOLITH.getDefaultState());
        PlayerEntity player = context.createMockSurvivalPlayer();
        BlockPos absolute = context.getAbsolutePos(pos);
        ModBlocks.REGOLITH.onSteppedOn(context.getWorld(), absolute, context.getWorld().getBlockState(absolute), player);
        context.assertTrue(context.getBlockState(pos).get(RegolithBlock.TRODDEN), "a footprint must stay in the regolith");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 2000)
    public void flightToTheMoonLandsOnAutopilot(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        server.getGameRules().get(ModGameRules.ENGINEER_MODE).set(false, server);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        RocketEntity rocket = FlightGameTestSupport.rocketOnPad(context, new BlockPos(3, 1, 3), RocketGameTest.moonRocket(), 1.0);
        FlightController.launch(rocket, player, Destination.MOON_LANDING);
        rocket.flight().skip();
        rocket.flight().requestManeuver();
        boolean[] transferSkipped = {false};

        context.addInstantFinalTask(() -> {
            RocketEntity current = player.getVehicle() instanceof RocketEntity r ? r : null;
            if (current != null && current.phase() == FlightPhase.TRANSFER && !transferSkipped[0]) {
                transferSkipped[0] = true;
                current.flight().skip();
            }
            context.assertTrue(player.getWorld().getRegistryKey() == ModDimensions.MOON, "the crew must be on the Moon");
            List<RocketEntity> landed = player.getServerWorld().getEntitiesByType(ModEntities.ROCKET,
                    new Box(player.getBlockPos()).expand(8), r -> r.phase() == FlightPhase.IDLE);
            context.assertTrue(!landed.isEmpty(), "the rocket must stand on the Moon");
            context.assertTrue(player.getVehicle() == null, "the crew must step out");
            context.assertTrue(SpaceProgram.get(server).hasFirst("moon_landing"), "the first Moon landing must be recorded");
            context.assertTrue(landed.get(0).fill() > 0, "fuel left over from the landing must stay in the tanks");
            landed.forEach(RocketEntity::discard);
            server.getGameRules().get(ModGameRules.ENGINEER_MODE).set(true, server);
            server.getPlayerManager().remove(player);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 2000)
    public void returnFromTheMoonLandsOnEarth(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerWorld moon = moon(context);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        moon.getChunk(2, 2);
        int ground = moon.getTopY(Heightmap.Type.MOTION_BLOCKING, 40, 40);
        FabricDimensions.teleport(player, moon, new TeleportTarget(new Vec3d(42.5, ground, 40.5), Vec3d.ZERO, 0, 0));
        RocketEntity rocket = ModEntities.ROCKET.create(moon);
        rocket.refreshPositionAndAngles(40.5, ground, 40.5, 0, 0);
        RocketDesign design = new RocketDesign(RocketGameTest.smallRocket());
        rocket.setDesign(design);
        rocket.setFuel(design.propellantCapacity(), design.propellantCapacity());
        moon.spawnEntity(rocket);

        context.assertTrue(FlightController.whyCannotLaunch(rocket, player, Destination.RETURN_TO_EARTH) == null,
                "a fuelled rocket on the Moon must be able to fly home");
        context.assertTrue(FlightController.whyCannotLaunch(rocket, player, Destination.EARTH_ORBIT) != null,
                "Earth destinations are not available from the Moon");
        FlightController.launch(rocket, player, Destination.RETURN_TO_EARTH);
        rocket.flight().skip();
        boolean[] transferSkipped = {false};

        context.addInstantFinalTask(() -> {
            RocketEntity current = player.getVehicle() instanceof RocketEntity r ? r : null;
            if (current != null && current.phase() == FlightPhase.TRANSFER && !transferSkipped[0]) {
                transferSkipped[0] = true;
                current.flight().skip();
            }
            context.assertTrue(player.getWorld().getRegistryKey() == World.OVERWORLD, "the crew must be back on Earth");
            context.assertTrue(player.getVehicle() == null, "the crew must step out of the capsule");
            List<RocketEntity> capsules = player.getServerWorld().getEntitiesByType(ModEntities.ROCKET,
                    new Box(player.getBlockPos()).expand(8), r -> r.phase() == FlightPhase.LANDED);
            context.assertTrue(!capsules.isEmpty(), "the capsule must have landed on Earth");
            capsules.forEach(RocketEntity::discard);
            server.getPlayerManager().remove(player);
        });
    }
}
