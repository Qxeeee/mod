package com.orbitcore.gametest;

import com.orbitcore.block.entity.RegolithProcessorBlockEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModItems;
import com.orbitcore.world.LunarEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Этап 0.8: переработчик реголита, гелий-3, кислород из ильменита, метеориты.
 */
public class LunarContentGameTest implements FabricGameTest {
    private static RegolithProcessorBlockEntity processor(TestContext context, BlockPos pos) {
        context.setBlockState(pos, ModBlocks.REGOLITH_PROCESSOR);
        RegolithProcessorBlockEntity processor = (RegolithProcessorBlockEntity) context.getBlockEntity(pos);
        processor.energy.amount = processor.energy.capacity;
        return processor;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void mareRegolithGivesHelium3(TestContext context) {
        RegolithProcessorBlockEntity processor = processor(context, new BlockPos(2, 1, 2));
        processor.setStack(RegolithProcessorBlockEntity.INPUT, new ItemStack(ModBlocks.MARIA_REGOLITH, 2));
        context.addInstantFinalTask(() -> {
            context.assertTrue(processor.getStack(RegolithProcessorBlockEntity.INPUT).isEmpty(), "all regolith must be processed");
            context.assertTrue(processor.helium() == 70, "two mare regolith must give 70 helium-3, got " + processor.helium());
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void ilmeniteGivesOxygenAndIron(TestContext context) {
        RegolithProcessorBlockEntity processor = processor(context, new BlockPos(2, 1, 2));
        processor.setStack(RegolithProcessorBlockEntity.INPUT, new ItemStack(ModItems.RAW_ILMENITE));
        context.addInstantFinalTask(() -> {
            context.assertTrue(processor.oxygen.amountMb() == 250, "ilmenite must give 250 mB of oxygen, got " + processor.oxygen.amountMb());
            ItemStack iron = processor.getStack(RegolithProcessorBlockEntity.OUTPUT);
            context.assertTrue(iron.isOf(Items.IRON_NUGGET) && iron.getCount() == 2, "ilmenite must leave iron nuggets, got " + iron);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heliumFillsCanister(TestContext context) {
        RegolithProcessorBlockEntity processor = processor(context, new BlockPos(2, 1, 2));
        NbtCompound nbt = processor.createNbt();
        nbt.putInt("Helium", 1200);
        processor.readNbt(nbt);
        processor.setStack(RegolithProcessorBlockEntity.CANISTER_IN, new ItemStack(ModItems.GAS_CANISTER, 2));
        context.addInstantFinalTask(() -> {
            context.assertTrue(processor.getStack(RegolithProcessorBlockEntity.CANISTER_OUT).isOf(ModItems.HELIUM3_CANISTER),
                    "a full canister of helium-3 must come out");
            context.assertTrue(processor.helium() == 200, "a canister takes 1000 helium-3, left " + processor.helium());
            context.assertTrue(processor.getStack(RegolithProcessorBlockEntity.CANISTER_IN).getCount() == 1,
                    "only one canister can be filled from 1200");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void meteoriteLeavesCraterAndRock(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos target = context.getAbsolutePos(new BlockPos(4, 0, 4));
        LunarEvents.spawnMeteor(world, target, 2);
        context.addInstantFinalTask(() -> {
            boolean found = false;
            for (BlockPos pos : BlockPos.iterate(target.add(-3, -6, -3), target.add(3, 3, 3))) {
                if (world.getBlockState(pos).isOf(ModBlocks.METEORITE) || world.getBlockState(pos).isOf(ModBlocks.HOT_METEORITE)) {
                    found = true;
                    break;
                }
            }
            context.assertTrue(found, "the meteorite must lie in its crater");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void bigMeteorLeavesAHotRockInItsCrater(TestContext context) {
        ServerWorld moon = context.getWorld();
        BlockPos target = context.getAbsolutePos(new BlockPos(4, 0, 4));
        LunarEvents.spawnMeteor(moon, target, 5);
        context.addInstantFinalTask(() -> {
            int hot = 0;
            for (BlockPos pos : BlockPos.iterate(target.add(-4, -9, -4), target.add(4, 3, 4))) {
                if (moon.getBlockState(pos).isOf(ModBlocks.HOT_METEORITE)) {
                    hot++;
                }
            }
            context.assertTrue(hot >= 1, "a big meteor must leave a glowing rock in its crater");
        });
    }
}
