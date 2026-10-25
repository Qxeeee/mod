package com.orbitcore.gametest;

import com.orbitcore.entity.RocketEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketDesign.Destination;
import com.orbitcore.rocket.RocketPart;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Этап 0.5: старт, подъём, отделение ступеней, орбита и посадка капсулы.
 */
public class FlightGameTest implements FabricGameTest {
    private static final BlockPos PAD = new BlockPos(3, 1, 3);

    private static RocketEntity rocketOnPad(TestContext context, List<RocketDesign.Entry> parts, double fill) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                context.setBlockState(PAD.add(dx, 0, dz), ModBlocks.LAUNCH_PAD.getDefaultState());
            }
        }
        ServerWorld world = context.getWorld();
        BlockPos above = context.getAbsolutePos(PAD.up());
        RocketEntity rocket = ModEntities.ROCKET.create(world);
        rocket.refreshPositionAndAngles(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, 0, 0);
        RocketDesign design = new RocketDesign(parts);
        rocket.setDesign(design);
        int fuel = (int) (design.propellantCapacity() * fill);
        rocket.setFuel(fuel, fuel);
        world.spawnEntity(rocket);
        return rocket;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void launchChecksRocket(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        RocketEntity empty = rocketOnPad(context, RocketGameTest.smallRocket(), 0.0);
        context.assertTrue(FlightController.whyCannotLaunch(empty, player, Destination.SUBORBITAL) != null, "an empty rocket must not launch");
        empty.setFuel(empty.design().propellantCapacity(), empty.design().propellantCapacity());
        context.assertTrue(FlightController.whyCannotLaunch(empty, player, Destination.SUBORBITAL) == null, "a fuelled small rocket can go suborbital");
        context.assertTrue(FlightController.whyCannotLaunch(empty, player, Destination.EARTH_ORBIT) != null, "a small rocket must not reach orbit");
        context.assertTrue(FlightController.whyCannotLaunch(empty, player, Destination.MOON_LANDING) != null, "the Moon is not available yet");

        List<RocketDesign.Entry> noCapsule = RocketGameTest.smallRocket().subList(0, 3);
        empty.setDesign(new RocketDesign(noCapsule));
        context.assertTrue(FlightController.whyCannotLaunch(empty, player, Destination.SUBORBITAL) != null, "no capsule, no crew");
        empty.discard();
        player.getServer().getPlayerManager().remove(player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1200)
    public void suborbitalHopLandsCapsule(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        RocketEntity rocket = rocketOnPad(context, RocketGameTest.smallRocket(), 1.0);
        SpaceProgram program = SpaceProgram.get(context.getWorld().getServer());
        int telemetry = program.points(ScienceType.TELEMETRY);

        FlightController.launch(rocket, player, Destination.SUBORBITAL);
        context.assertTrue(player.getVehicle() == rocket, "the crew must board the capsule");
        context.assertTrue(rocket.phase() == FlightPhase.COUNTDOWN, "launch starts with a countdown");

        context.addInstantFinalTask(() -> {
            context.assertTrue(rocket.phase() == FlightPhase.LANDED, "capsule not landed yet: " + rocket.phase());
            context.assertTrue(rocket.design().parts().size() == 1
                    && rocket.design().parts().get(0).part() == RocketPart.CAPSULE, "only the capsule comes back");
            context.assertTrue(player.getVehicle() == null, "the crew must step out after landing");
            context.assertTrue(program.points(ScienceType.TELEMETRY) >= telemetry + 4, "a suborbital hop gives at least 4 telemetry");
            rocket.discard();
            player.getServer().getPlayerManager().remove(player);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1800)
    public void orbitalFlightDropsStageAndReturns(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        RocketEntity rocket = rocketOnPad(context, RocketGameTest.moonRocket(), 1.0);
        FlightController.launch(rocket, player, Destination.EARTH_ORBIT);
        boolean[] sawSpace = {false};

        context.addInstantFinalTask(() -> {
            if (rocket.phase() == FlightPhase.SPACE && !sawSpace[0]) {
                sawSpace[0] = true;
                context.assertTrue(rocket.design().stages().size() == 1, "the first stage must have separated on the way up");
                context.assertTrue(rocket.separationTick() > 0, "separation must be recorded for the camera");
                rocket.flight().requestDeorbit();
            }
            context.assertTrue(sawSpace[0] && rocket.phase() == FlightPhase.LANDED, "not back yet: " + rocket.phase());
            rocket.discard();
            player.getServer().getPlayerManager().remove(player);
        });
    }
}
