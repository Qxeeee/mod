package com.orbitcore.gametest;

import com.orbitcore.OrbitCore;
import com.orbitcore.block.MachineBlock;
import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.ElectrolyzerBlockEntity;
import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModRecipes;
import com.orbitcore.registry.ModWorldGen;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import team.reborn.energy.api.EnergyStorage;

import java.util.Optional;

/**
 * Этап 0.2: руды, металлы и машины Земли.
 */
public class EarthMaterialsGameTest implements FabricGameTest {
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(2, 1, 1);

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1000)
    public void furnaceSmeltsSteelOnlyWhenHot(TestContext context) {
        context.setBlockState(FIRST, ModBlocks.METALLURGICAL_FURNACE.getDefaultState());
        MetallurgicalFurnaceBlockEntity furnace = (MetallurgicalFurnaceBlockEntity) context.getBlockEntity(FIRST);
        furnace.setStack(MetallurgicalFurnaceBlockEntity.INPUT, new ItemStack(Items.IRON_INGOT));
        furnace.setStack(MetallurgicalFurnaceBlockEntity.REDUCTANT, new ItemStack(Items.COAL));
        furnace.setStack(MetallurgicalFurnaceBlockEntity.FUEL, new ItemStack(Items.COAL, 4));

        // Через 10 секунд печь горит и греется, но до 1200 °C ещё не дошла — стали быть не должно
        context.runAtTick(200, () -> {
            context.assertTrue(context.getBlockState(FIRST).get(MachineBlock.LIT), "furnace is not lit");
            context.assertTrue(furnace.getHeat() > 500, "furnace is not heating up: " + furnace.getHeat());
            context.assertTrue(furnace.getStack(MetallurgicalFurnaceBlockEntity.OUTPUT).isEmpty(),
                    "steel appeared before the furnace reached 1200 °C");
        });
        context.addInstantFinalTask(() -> {
            context.assertTrue(furnace.getStack(MetallurgicalFurnaceBlockEntity.OUTPUT).isOf(ModItems.STEEL_INGOT), "no steel yet");
            context.assertTrue(furnace.getStack(MetallurgicalFurnaceBlockEntity.INPUT).isEmpty(), "iron was not consumed");
            context.assertTrue(furnace.getStack(MetallurgicalFurnaceBlockEntity.REDUCTANT).isEmpty(), "coal was not consumed");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void generatorPowersElectrolyzer(TestContext context) {
        context.setBlockState(FIRST, ModBlocks.COAL_GENERATOR.getDefaultState());
        context.setBlockState(SECOND, ModBlocks.ELECTROLYZER.getDefaultState());
        CoalGeneratorBlockEntity generator = (CoalGeneratorBlockEntity) context.getBlockEntity(FIRST);
        ElectrolyzerBlockEntity electrolyzer = (ElectrolyzerBlockEntity) context.getBlockEntity(SECOND);
        generator.setStack(CoalGeneratorBlockEntity.FUEL, new ItemStack(Items.COAL));
        electrolyzer.setStack(ElectrolyzerBlockEntity.INPUT, new ItemStack(ModItems.RAW_BAUXITE, 2));

        context.addInstantFinalTask(() -> context.assertTrue(
                electrolyzer.getStack(ElectrolyzerBlockEntity.OUTPUT).isOf(ModItems.ALUMINUM_INGOT), "no aluminum yet"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void machinesExposeEnergy(TestContext context) {
        context.setBlockState(FIRST, ModBlocks.COAL_GENERATOR.getDefaultState());
        context.setBlockState(SECOND, ModBlocks.ELECTROLYZER.getDefaultState());
        ServerWorld world = context.getWorld();
        EnergyStorage generator = EnergyStorage.SIDED.find(world, context.getAbsolutePos(FIRST), Direction.UP);
        EnergyStorage electrolyzer = EnergyStorage.SIDED.find(world, context.getAbsolutePos(SECOND), Direction.UP);
        context.assertTrue(generator != null && generator.supportsExtraction() && !generator.supportsInsertion(),
                "generator must only give energy");
        context.assertTrue(electrolyzer != null && electrolyzer.supportsInsertion() && !electrolyzer.supportsExtraction(),
                "electrolyzer must only take energy");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void platesNeedEngineeringTable(TestContext context) {
        ServerWorld world = context.getWorld();
        Optional<? extends Recipe<?>> recipe = world.getRecipeManager().get(OrbitCore.id("steel_plate"));
        context.assertTrue(recipe.isPresent() && recipe.get().getType() == ModRecipes.ENGINEERING,
                "steel_plate must be an engineering recipe");

        CraftingInventory grid = new CraftingInventory(new DummyHandler(), 3, 3);
        grid.setStack(0, new ItemStack(ModItems.STEEL_INGOT));
        grid.setStack(3, new ItemStack(ModItems.STEEL_INGOT));
        context.assertTrue(world.getRecipeManager().getFirstMatch(ModRecipes.ENGINEERING, grid, world)
                        .map(r -> r.craft(grid, world.getRegistryManager()).isOf(ModItems.STEEL_PLATE)).orElse(false),
                "two steel ingots must make a steel plate on the engineering table");
        context.assertTrue(world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, grid, world).isEmpty(),
                "a regular crafting table must not make plates");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void recipesAndOresAreLoaded(TestContext context) {
        ServerWorld world = context.getWorld();
        context.assertTrue(world.getRecipeManager().listAllOfType(ModRecipes.METALLURGY).size() >= 4, "metallurgy recipes missing");
        context.assertTrue(!world.getRecipeManager().listAllOfType(ModRecipes.ELECTROLYSIS).isEmpty(), "electrolysis recipes missing");
        var placed = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE);
        context.assertTrue(placed.contains(ModWorldGen.ORE_BAUXITE), "bauxite ore generation missing");
        context.assertTrue(placed.contains(ModWorldGen.ORE_RUTILE), "rutile ore generation missing");
        context.complete();
    }

    /** Пустой интерфейс: нужен только чтобы собрать сетку верстака в тесте. */
    private static class DummyHandler extends ScreenHandler {
        DummyHandler() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }
    }
}
