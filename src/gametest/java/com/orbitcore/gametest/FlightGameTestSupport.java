package com.orbitcore.gametest;

import com.orbitcore.entity.RocketEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.rocket.RocketDesign;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Общее для тестов полёта: стартовый стол с заправленной ракетой.
 */
final class FlightGameTestSupport {
    private FlightGameTestSupport() {
    }

    static RocketEntity rocketOnPad(TestContext context, BlockPos pad, List<RocketDesign.Entry> parts, double fill) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                context.setBlockState(pad.add(dx, 0, dz), ModBlocks.LAUNCH_PAD.getDefaultState());
            }
        }
        ServerWorld world = context.getWorld();
        BlockPos above = context.getAbsolutePos(pad.up());
        RocketEntity rocket = ModEntities.ROCKET.create(world);
        rocket.refreshPositionAndAngles(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, 0, 0);
        RocketDesign design = new RocketDesign(parts);
        rocket.setDesign(design);
        int fuel = (int) (design.propellantCapacity() * fill);
        rocket.setFuel(fuel, fuel);
        world.spawnEntity(rocket);
        return rocket;
    }
}
