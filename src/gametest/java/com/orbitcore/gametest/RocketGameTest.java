package com.orbitcore.gametest;

import com.orbitcore.block.entity.AirSeparatorBlockEntity;
import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.FuelRefineryBlockEntity;
import com.orbitcore.block.entity.RocketAssemblerBlockEntity;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.item.RocketItem;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModItems;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketDesign.Destination;
import com.orbitcore.rocket.RocketDesign.Problem;
import com.orbitcore.rocket.RocketPart;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Этап 0.4: расчёт ракеты, сборка, топливо, стартовый стол.
 */
public class RocketGameTest implements FabricGameTest {
    /** Двухступенчатая ракета, которой хватает на посадку на Луну. */
    static List<RocketDesign.Entry> moonRocket() {
        return List.of(
                new RocketDesign.Entry(RocketPart.ENGINE_FAKEL, 2),
                new RocketDesign.Entry(RocketPart.FINS, 1),
                new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1),
                new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1),
                new RocketDesign.Entry(RocketPart.STAGE_DECOUPLER, 1),
                new RocketDesign.Entry(RocketPart.ENGINE_ISKRA, 1),
                new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1),
                new RocketDesign.Entry(RocketPart.LANDING_LEGS, 1),
                new RocketDesign.Entry(RocketPart.CAPSULE, 1));
    }

    static List<RocketDesign.Entry> smallRocket() {
        return List.of(
                new RocketDesign.Entry(RocketPart.ENGINE_ISKRA, 1),
                new RocketDesign.Entry(RocketPart.FINS, 1),
                new RocketDesign.Entry(RocketPart.FUEL_TANK_SMALL, 1),
                new RocketDesign.Entry(RocketPart.LANDING_LEGS, 1),
                new RocketDesign.Entry(RocketPart.CAPSULE, 1));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rocketMathMatchesTsiolkovsky(TestContext context) {
        RocketDesign small = new RocketDesign(smallRocket());
        context.assertTrue(small.isValid() && small.stages().size() == 1, "small rocket must be a valid single stage");
        double expected = 290 * RocketDesign.G0 * Math.log(8.1 / 4.1);
        context.assertTrue(Math.abs(small.deltaV(1.0) - expected) < 1, "small rocket Δv " + small.deltaV(1.0) + " != " + expected);
        context.assertTrue(small.canReach(Destination.SUBORBITAL, 1.0), "small rocket should go suborbital");
        context.assertTrue(!small.canReach(Destination.EARTH_ORBIT, 1.0), "small rocket must not reach orbit");
        context.assertTrue(small.deltaV(0.5) < small.deltaV(1.0), "half-fuelled rocket has less Δv");

        RocketDesign moon = new RocketDesign(moonRocket());
        context.assertTrue(moon.isValid() && moon.stages().size() == 2, "moon rocket must have two stages");
        context.assertTrue(Math.abs(moon.liftoffTwr(1.0) - 1.65) < 0.01, "moon rocket TWR " + moon.liftoffTwr(1.0));
        context.assertTrue(moon.canReach(Destination.MOON_LANDING, 1.0), "moon rocket must reach the Moon: Δv " + moon.deltaV(1.0));
        context.assertTrue(!moon.canReach(Destination.MOON_RETURN, 1.0), "moon rocket must not be enough for a round trip");
        context.assertTrue(moon.problems().isEmpty(), "moon rocket has no warnings: " + moon.problems());

        RocketDesign roundTrip = RocketDesign.fromNbt(moon.toNbt());
        context.assertTrue(Math.abs(roundTrip.deltaV(1.0) - moon.deltaV(1.0)) < 1e-6, "design must survive saving");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void badRocketsAreRejected(TestContext context) {
        context.assertTrue(new RocketDesign(List.of()).problems().contains(Problem.EMPTY), "empty");
        context.assertTrue(new RocketDesign(List.of(new RocketDesign.Entry(RocketPart.FUEL_TANK_SMALL, 1),
                new RocketDesign.Entry(RocketPart.ENGINE_ISKRA, 1))).problems().contains(Problem.ENGINE_NOT_AT_BOTTOM), "engine on top");
        context.assertTrue(new RocketDesign(List.of(new RocketDesign.Entry(RocketPart.ENGINE_ISKRA, 1)))
                .problems().contains(Problem.NO_TANK), "no tank");
        context.assertTrue(new RocketDesign(List.of(new RocketDesign.Entry(RocketPart.ENGINE_ISKRA, 1),
                new RocketDesign.Entry(RocketPart.CAPSULE, 1), new RocketDesign.Entry(RocketPart.FUEL_TANK_SMALL, 1)))
                .problems().contains(Problem.CAPSULE_NOT_ON_TOP), "capsule in the middle");

        RocketDesign heavy = new RocketDesign(List.of(new RocketDesign.Entry(RocketPart.ENGINE_ISKRA, 1),
                new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1), new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1),
                new RocketDesign.Entry(RocketPart.CAPSULE, 1)));
        context.assertTrue(heavy.isValid() && heavy.problems().contains(Problem.LOW_THRUST), "too heavy is a warning, not an error");
        context.assertTrue(!heavy.canReach(Destination.SUBORBITAL, 1.0), "a rocket that cannot lift off goes nowhere");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void assemblerBuildsRocket(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.ROCKET_ASSEMBLER.getDefaultState());
        RocketAssemblerBlockEntity assembler = (RocketAssemblerBlockEntity) context.getBlockEntity(pos);
        assembler.setStack(0, new ItemStack(ModItems.ENGINE_ISKRA, 3));
        assembler.setStack(1, new ItemStack(ModItems.FUEL_TANK_SMALL));
        assembler.setStack(2, new ItemStack(ModItems.CAPSULE_KAPLYA));

        context.assertTrue(assembler.assemble(), "a valid rocket must assemble");
        ItemStack rocket = assembler.getStack(RocketAssemblerBlockEntity.OUTPUT);
        context.assertTrue(rocket.isOf(ModItems.ASSEMBLED_ROCKET), "assembled rocket must appear in the output");
        RocketDesign design = RocketItem.design(rocket);
        context.assertTrue(design.parts().size() == 3 && design.parts().get(0).count() == 3, "three-engine cluster must be kept");
        context.assertTrue(assembler.getStack(0).isEmpty() && assembler.getStack(2).isEmpty(), "parts must be consumed");
        context.assertTrue(!assembler.assemble(), "cannot assemble again with an empty stand and full output");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
    public void refineryMakesKerosene(TestContext context) {
        BlockPos refineryPos = new BlockPos(1, 1, 1);
        BlockPos generatorPos = new BlockPos(2, 1, 1);
        context.setBlockState(refineryPos, ModBlocks.FUEL_REFINERY.getDefaultState());
        context.setBlockState(generatorPos, ModBlocks.COAL_GENERATOR.getDefaultState());
        ((CoalGeneratorBlockEntity) context.getBlockEntity(generatorPos)).setStack(CoalGeneratorBlockEntity.FUEL, new ItemStack(Items.COAL, 2));
        FuelRefineryBlockEntity refinery = (FuelRefineryBlockEntity) context.getBlockEntity(refineryPos);
        refinery.setStack(FuelRefineryBlockEntity.COAL, new ItemStack(Items.COAL, 2));
        refinery.setStack(FuelRefineryBlockEntity.WATER_IN, new ItemStack(Items.WATER_BUCKET));
        refinery.setStack(FuelRefineryBlockEntity.FILL_IN, new ItemStack(Items.BUCKET));

        context.addInstantFinalTask(() -> {
            context.assertTrue(refinery.getStack(FuelRefineryBlockEntity.BUCKET_OUT).isOf(Items.BUCKET), "the water bucket must come back empty");
            context.assertTrue(refinery.getStack(FuelRefineryBlockEntity.FILL_OUT).isOf(ModFluids.KEROSENE_BUCKET), "no kerosene bucket yet");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 500)
    public void separatorMakesLiquidOxygen(TestContext context) {
        BlockPos separatorPos = new BlockPos(1, 1, 1);
        BlockPos generatorPos = new BlockPos(2, 1, 1);
        context.setBlockState(separatorPos, ModBlocks.AIR_SEPARATOR.getDefaultState());
        context.setBlockState(generatorPos, ModBlocks.COAL_GENERATOR.getDefaultState());
        ((CoalGeneratorBlockEntity) context.getBlockEntity(generatorPos)).setStack(CoalGeneratorBlockEntity.FUEL, new ItemStack(Items.COAL));
        AirSeparatorBlockEntity separator = (AirSeparatorBlockEntity) context.getBlockEntity(separatorPos);
        separator.setStack(AirSeparatorBlockEntity.FILL_IN, new ItemStack(Items.BUCKET));

        context.addInstantFinalTask(() -> context.assertTrue(
                separator.getStack(AirSeparatorBlockEntity.FILL_OUT).isOf(ModFluids.LIQUID_OXYGEN_BUCKET), "no oxygen bucket yet"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rocketStandsOnCompletePadOnly(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = new BlockPos(3, 1, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                context.setBlockState(center.add(dx, 0, dz), ModBlocks.LAUNCH_PAD.getDefaultState());
            }
        }
        context.setBlockState(center.add(1, 0, 1), Blocks.STONE.getDefaultState());
        PlayerEntity player = context.createMockCreativePlayer();
        ItemStack rocketItem = RocketItem.create(ModItems.ASSEMBLED_ROCKET, new RocketDesign(smallRocket()), 0, 0);

        context.assertTrue(place(context, player, rocketItem, center) == ActionResult.FAIL, "incomplete pad must refuse the rocket");
        context.setBlockState(center.add(1, 0, 1), ModBlocks.LAUNCH_PAD.getDefaultState());
        context.assertTrue(place(context, player, rocketItem, center).isAccepted(), "complete pad must accept the rocket");

        BlockPos above = context.getAbsolutePos(center.up());
        List<RocketEntity> rockets = world.getEntitiesByClass(RocketEntity.class, new Box(above).expand(0.5, 3, 0.5), e -> true);
        context.assertTrue(rockets.size() == 1, "exactly one rocket must stand on the pad, found " + rockets.size());
        RocketEntity rocket = rockets.get(0);
        context.assertTrue(rocket.design().parts().size() == 5, "rocket must keep its modules");
        context.assertTrue(Math.abs(rocket.getHeight() - rocket.design().height()) < 0.01, "hitbox must match the rocket height");

        // Заправка вёдрами
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModFluids.KEROSENE_BUCKET));
        rocket.interact(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModFluids.LIQUID_OXYGEN_BUCKET));
        rocket.interact(player, Hand.MAIN_HAND);
        context.assertTrue(rocket.kerosene() == 1000 && rocket.oxygen() == 1000, "one bucket of each must be loaded");
        context.assertTrue(Math.abs(rocket.fill() - 0.5) < 1e-6, "small tank is half full");

        // Ракета переживает сохранение
        NbtCompound nbt = new NbtCompound();
        rocket.writeNbt(nbt);
        RocketEntity copy = ModEntities.ROCKET.create(world);
        copy.readNbt(nbt);
        context.assertTrue(copy.design().parts().size() == 5 && copy.kerosene() == 1000, "rocket must survive saving");
        rocket.discard();
        context.complete();
    }

    private static ActionResult place(TestContext context, PlayerEntity player, ItemStack stack, BlockPos relative) {
        BlockPos pos = context.getAbsolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        return stack.copy().useOnBlock(new ItemUsageContext(context.getWorld(), player, Hand.MAIN_HAND, stack.copy(), hit));
    }
}
