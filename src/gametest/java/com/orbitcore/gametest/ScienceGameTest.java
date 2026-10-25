package com.orbitcore.gametest;

import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.SampleAnalyzerBlockEntity;
import com.orbitcore.recipe.EngineeringRecipe;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModRecipes;
import com.orbitcore.science.CelestialBody;
import com.orbitcore.science.CosmopediaEntry;
import com.orbitcore.science.ObservationTarget;
import com.orbitcore.science.Observatory;
import com.orbitcore.science.Research;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.science.SpaceProgram.ResearchStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Этап 0.3: космическая программа, телескоп, анализатор и исследования.
 */
public class ScienceGameTest implements FabricGameTest {
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(2, 1, 1);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void researchTreeFollowsRules(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        SpaceProgram program = new SpaceProgram();

        context.assertTrue(program.status(Research.ASTRONOMY) == ResearchStatus.DONE, "astronomy must be known from the start");
        context.assertTrue(program.status(Research.SPECTROSCOPY) == ResearchStatus.NOT_ENOUGH_POINTS, "spectroscopy must cost points");
        context.assertTrue(program.status(Research.ROCKETRY) == ResearchStatus.LOCKED, "rocketry must need spectroscopy first");

        program.addPoints(server, ScienceType.OBSERVATION, 30);
        program.addPoints(server, ScienceType.SAMPLE, 20);
        context.assertTrue(program.unlock(server, Research.SPECTROSCOPY), "spectroscopy should unlock with enough points");
        context.assertTrue(program.points(ScienceType.OBSERVATION) == 22, "spectroscopy must cost 8 observations");

        context.assertTrue(program.status(Research.ROCKETRY) == ResearchStatus.NEEDS_DISCOVERY, "rocketry must need the Moon");
        context.assertTrue(!program.unlock(server, Research.ROCKETRY), "rocketry must not unlock before the Moon is discovered");
        context.assertTrue(program.discover(server, CelestialBody.MOON), "first Moon discovery must count");
        context.assertTrue(!program.discover(server, CelestialBody.MOON), "the Moon can only be discovered once");
        context.assertTrue(program.hasEntry(CosmopediaEntry.MOON.id()), "discovering the Moon opens its Cosmopedia article");
        context.assertTrue(program.unlock(server, Research.ROCKETRY), "rocketry should unlock after the Moon is discovered");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void observationsOncePerNight(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        SpaceProgram program = new SpaceProgram();
        context.assertTrue(program.recordObservation(server, ObservationTarget.ORION, 10), "first observation counts");
        context.assertTrue(!program.recordObservation(server, ObservationTarget.ORION, 10), "same night must not count twice");
        context.assertTrue(program.recordObservation(server, ObservationTarget.CYGNUS, 10), "other targets still count");
        context.assertTrue(program.recordObservation(server, ObservationTarget.ORION, 11), "next night counts again");
        context.assertTrue(program.points(ScienceType.OBSERVATION) == 6, "three constellation photos give 6 observations");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void programSurvivesSaving(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        SpaceProgram program = new SpaceProgram();
        program.addPoints(server, ScienceType.SAMPLE, 7);
        program.discover(server, CelestialBody.MOON);
        program.recordObservation(server, ObservationTarget.MOON, 3);
        program.recordAnalysis(server, new Identifier("minecraft:quartz"), 1, null);

        SpaceProgram loaded = SpaceProgram.fromNbt(program.writeNbt(new NbtCompound()));
        context.assertTrue(loaded.points(ScienceType.SAMPLE) == 8, "sample points must be saved");
        context.assertTrue(loaded.points(ScienceType.OBSERVATION) == 5, "observation points must be saved");
        context.assertTrue(loaded.isDiscovered(CelestialBody.MOON), "discoveries must be saved");
        context.assertTrue(loaded.observedToday(ObservationTarget.MOON, 3), "observation nights must be saved");
        context.assertTrue(loaded.timesAnalyzed(new Identifier("minecraft:quartz")) == 1, "analysis counts must be saved");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void telescopeOnlyWorksAtNight(TestContext context) {
        ServerWorld world = context.getWorld();
        context.setBlockState(FIRST, ModBlocks.TELESCOPE.getDefaultState());
        BlockPos telescope = context.getAbsolutePos(FIRST);
        long before = world.getTimeOfDay();

        world.setTimeOfDay(6000);
        Text day = Observatory.whyCannotObserve(world, telescope);
        context.assertTrue(day != null && key(day).equals("message.orbitcore.telescope.daytime"), "noon must be too bright");
        world.setTimeOfDay(18000);
        Text night = Observatory.whyCannotObserve(world, telescope);
        context.assertTrue(night == null || !key(night).equals("message.orbitcore.telescope.daytime"), "midnight must not be daytime");

        world.setTimeOfDay(before);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 900)
    public void analyzerStopsWhenSampleIsStudied(TestContext context) {
        context.setBlockState(FIRST, ModBlocks.COAL_GENERATOR.getDefaultState());
        context.setBlockState(SECOND, ModBlocks.SAMPLE_ANALYZER.getDefaultState());
        CoalGeneratorBlockEntity generator = (CoalGeneratorBlockEntity) context.getBlockEntity(FIRST);
        SampleAnalyzerBlockEntity analyzer = (SampleAnalyzerBlockEntity) context.getBlockEntity(SECOND);
        generator.setStack(CoalGeneratorBlockEntity.FUEL, new ItemStack(Items.COAL, 2));
        analyzer.setStack(SampleAnalyzerBlockEntity.INPUT, new ItemStack(ModItems.RAW_RUTILE, SpaceProgram.ANALYSIS_LIMIT + 1));

        SpaceProgram program = SpaceProgram.get(context.getWorld().getServer());
        Identifier rutile = Registries.ITEM.getId(ModItems.RAW_RUTILE);
        int samplesBefore = program.points(ScienceType.SAMPLE);
        context.addInstantFinalTask(() -> {
            context.assertTrue(program.timesAnalyzed(rutile) == SpaceProgram.ANALYSIS_LIMIT, "rutile analyzed " + program.timesAnalyzed(rutile) + " times");
            context.assertTrue(analyzer.getStack(SampleAnalyzerBlockEntity.INPUT).getCount() == 1, "the extra sample must stay in the analyzer");
            context.assertTrue(program.points(ScienceType.SAMPLE) - samplesBefore == 3 * SpaceProgram.ANALYSIS_LIMIT, "each rutile gives 3 samples");
            context.assertTrue(program.hasEntry(CosmopediaEntry.RUTILE.id()), "rutile article must open");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void analyzerRecipeNeedsSpectroscopy(TestContext context) {
        ServerWorld world = context.getWorld();
        var recipe = world.getRecipeManager().get(new Identifier("orbitcore", "sample_analyzer"))
                .filter(r -> r.getType() == ModRecipes.ENGINEERING)
                .map(r -> (EngineeringRecipe) r);
        context.assertTrue(recipe.isPresent(), "sample analyzer must be an engineering recipe");
        context.assertTrue(Research.SPECTROSCOPY.id().equals(recipe.get().research()), "sample analyzer must need spectroscopy");
        context.complete();
    }

    private static String key(Text text) {
        return text.getContent() instanceof TranslatableTextContent t ? t.getKey() : "";
    }
}
