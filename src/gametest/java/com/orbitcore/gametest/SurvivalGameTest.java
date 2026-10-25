package com.orbitcore.gametest;

import com.orbitcore.block.AirlockDoorBlock;
import com.orbitcore.block.MachineBlock;
import com.orbitcore.block.entity.OxygenStationBlockEntity;
import com.orbitcore.block.entity.SealerBlockEntity;
import com.orbitcore.item.OxygenTankItem;
import com.orbitcore.item.SpacesuitItem;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModItems;
import com.orbitcore.survival.LifeSupport;
import com.orbitcore.survival.LifeSupportComponent;
import com.orbitcore.survival.Oxygen;
import com.orbitcore.survival.SealedAreas;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

/**
 * Этап 0.7: герметизация, шлюз, дыхание в вакууме, скафандр, радиация, температура.
 */
public class SurvivalGameTest implements FabricGameTest {
    /**
     * Пустая каменная комната: стены от {@code min} до {@code max} включительно,
     * герметизатор встроен в западную стену решёткой внутрь.
     */
    private static SealerBlockEntity buildRoom(World world, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            boolean wall = pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getY() == min.getY()
                    || pos.getY() == max.getY() || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
            world.setBlockState(pos, wall ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        BlockPos sealerPos = new BlockPos(min.getX(), min.getY() + 1, (min.getZ() + max.getZ()) / 2);
        world.setBlockState(sealerPos, ModBlocks.SEALER.getDefaultState().with(MachineBlock.FACING, Direction.EAST));
        SealerBlockEntity sealer = (SealerBlockEntity) world.getBlockEntity(sealerPos);
        sealer.energy.amount = sealer.energy.capacity;
        sealer.oxygen.fillMb(4000);
        return sealer;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sealerPressurizesClosedRoomAndFindsLeak(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos min = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos max = context.getAbsolutePos(new BlockPos(6, 5, 6));
        SealerBlockEntity sealer = buildRoom(world, min, max);
        sealer.rescan(world, sealer.getPos());
        context.assertTrue(sealer.status() == SealerBlockEntity.Status.SEALED, "a closed room must be sealed, got " + sealer.status());
        context.assertTrue(sealer.volume() == 4 * 3 * 4, "room volume must be 48 blocks, got " + sealer.volume());
        context.assertTrue(SealedAreas.isSealed(world, min.add(2, 2, 2)), "air must fill the room");
        context.assertTrue(sealer.oxygen.amountMb() < 4000, "pressurizing must use liquid oxygen");

        // Пробиваем стену: воздух уходит, герметизатор показывает дыру
        BlockPos hole = new BlockPos(max.getX(), min.getY() + 2, min.getZ() + 2);
        world.setBlockState(hole, Blocks.AIR.getDefaultState());
        sealer.rescan(world, sealer.getPos());
        context.assertTrue(sealer.status() == SealerBlockEntity.Status.LEAK, "a room with a hole must leak, got " + sealer.status());
        context.assertTrue(hole.equals(sealer.leak()), "the leak must be found in the hole " + hole + ", got " + sealer.leak());
        context.assertTrue(!SealedAreas.isSealed(world, min.add(2, 2, 2)), "air must be gone after the leak");

        // Заделываем — снова герметично
        world.setBlockState(hole, Blocks.GLASS.getDefaultState());
        sealer.rescan(world, sealer.getPos());
        context.assertTrue(sealer.status() == SealerBlockEntity.Status.SEALED, "a patched room must be sealed again");
        world.removeBlock(sealer.getPos(), false);
        context.assertTrue(!SealedAreas.isSealed(world, min.add(2, 2, 2)), "a removed sealer must not hold air");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void airlockDoorSealsOnlyWhenClosed(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos min = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos max = context.getAbsolutePos(new BlockPos(5, 5, 5));
        SealerBlockEntity sealer = buildRoom(world, min, max);
        BlockPos lower = min.add(2, 1, 0);
        BlockState door = ModBlocks.AIRLOCK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
        world.setBlockState(lower, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        world.setBlockState(lower.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        sealer.rescan(world, sealer.getPos());
        context.assertTrue(sealer.status() == SealerBlockEntity.Status.SEALED, "a closed airlock door must hold air");

        world.setBlockState(lower, world.getBlockState(lower).with(AirlockDoorBlock.OPEN, true));
        world.setBlockState(lower.up(), world.getBlockState(lower.up()).with(AirlockDoorBlock.OPEN, true));
        sealer.rescan(world, sealer.getPos());
        context.assertTrue(sealer.status() == SealerBlockEntity.Status.LEAK, "an open airlock door must let the air out");

        // Обычная дубовая дверь воздух не держит даже закрытой
        BlockState oak = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
        world.setBlockState(lower, oak.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        world.setBlockState(lower.up(), oak.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        sealer.rescan(world, sealer.getPos());
        context.assertTrue(sealer.status() == SealerBlockEntity.Status.LEAK, "a wooden door is not airtight");
        world.removeBlock(sealer.getPos(), false);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sealerNeedsFreeOutlet(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos min = context.getAbsolutePos(new BlockPos(1, 1, 1));
        SealerBlockEntity sealer = buildRoom(world, min, min.add(4, 4, 4));
        world.setBlockState(sealer.getPos().east(), Blocks.STONE.getDefaultState());
        sealer.rescan(world, sealer.getPos());
        context.assertTrue(sealer.status() == SealerBlockEntity.Status.BLOCKED, "a covered outlet must be reported, got " + sealer.status());
        world.removeBlock(sealer.getPos(), false);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sealedRoomIsBreathableOnTheMoon(TestContext context) {
        ServerWorld moon = context.getWorld().getServer().getWorld(ModDimensions.MOON);
        moon.getChunk(6, 6);
        BlockPos min = new BlockPos(98, 250, 98);
        BlockPos max = new BlockPos(103, 254, 103);
        SealerBlockEntity sealer = buildRoom(moon, min, max);
        BlockPos inside = min.add(2, 2, 2);
        context.assertTrue(!LifeSupport.canBreathe(moon, inside), "there is no air on the Moon before sealing");
        sealer.rescan(moon, sealer.getPos());
        context.assertTrue(LifeSupport.canBreathe(moon, inside), "a sealed room on the Moon must be breathable");
        context.assertTrue(!LifeSupport.canBreathe(moon, max.up(2)), "outside the room is still vacuum");
        context.assertTrue(LifeSupport.temperature(moon, inside, true) == 21, "a pressurized room is kept warm");
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            moon.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        context.assertTrue(!LifeSupport.canBreathe(moon, inside), "a destroyed room holds no air");
        context.complete();
    }

    private static ServerPlayerEntity survivalPlayerOnTheMoon(TestContext context, int x, int z) {
        ServerWorld moon = context.getWorld().getServer().getWorld(ModDimensions.MOON);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        moon.getChunk(x >> 4, z >> 4);
        int ground = moon.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        FabricDimensions.teleport(player, moon, new TeleportTarget(new Vec3d(x + 0.5, ground, z + 0.5), Vec3d.ZERO, 0, 0));
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void vacuumSuffocatesWithoutSpacesuit(TestContext context) {
        ServerPlayerEntity player = survivalPlayerOnTheMoon(context, 120, 24);
        context.addInstantFinalTask(() -> {
            LifeSupportComponent data = LifeSupport.get(player);
            context.assertTrue(!data.breathable, "the Moon has no air");
            context.assertTrue(data.suffocating, "without a suit the player must suffocate");
            context.assertTrue(player.getAir() < player.getMaxAir() - 20, "air must run out, left " + player.getAir());
            context.getWorld().getServer().getPlayerManager().remove(player);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void spacesuitSuppliesOxygenOnTheMoon(TestContext context) {
        ServerPlayerEntity player = survivalPlayerOnTheMoon(context, 136, 24);
        player.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACESUIT_HELMET));
        ItemStack chest = new ItemStack(ModItems.SPACESUIT_CHESTPLATE);
        Oxygen.set(chest, SpacesuitItem.CHEST_OXYGEN);
        player.equipStack(EquipmentSlot.CHEST, chest);
        player.equipStack(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACESUIT_LEGGINGS));
        player.equipStack(EquipmentSlot.FEET, new ItemStack(ModItems.SPACESUIT_BOOTS));
        context.assertTrue(SpacesuitItem.isWearingFullSuit(player), "all four suit pieces must count as a full suit");

        context.addInstantFinalTask(() -> {
            LifeSupportComponent data = LifeSupport.get(player);
            ItemStack worn = player.getEquippedStack(EquipmentSlot.CHEST);
            context.assertTrue(Oxygen.stored(worn) <= SpacesuitItem.CHEST_OXYGEN - 3, "breathing must use suit oxygen");
            context.assertTrue(!data.suffocating, "a suit with oxygen must keep the player breathing");
            context.assertTrue(player.getAir() == player.getMaxAir(), "lungs must stay full in a suit");
            context.getWorld().getServer().getPlayerManager().remove(player);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void radiationAndTemperatureFollowTheSun(TestContext context) {
        ServerWorld earth = context.getWorld();
        ServerWorld moon = earth.getServer().getWorld(ModDimensions.MOON);
        moon.getChunk(9, 1);
        int ground = moon.getTopY(Heightmap.Type.MOTION_BLOCKING, 152, 24);
        BlockPos open = new BlockPos(152, ground + 2, 24);
        BlockPos buried = new BlockPos(152, ground - 12, 24);
        long time = earth.getTimeOfDay();
        try {
            earth.setTimeOfDay(6000);
            float noonRate = LifeSupport.radiationRate(moon, open, false, false);
            context.assertTrue(noonRate > 0.5f, "the lunar day must be radioactive, got " + noonRate);
            context.assertTrue(LifeSupport.radiationRate(moon, open, true, false) < noonRate, "a suit must reduce the dose");
            context.assertTrue(LifeSupport.radiationRate(moon, buried, false, false) == 0, "regolith overhead must stop radiation");
            context.assertTrue(LifeSupport.radiationRate(moon, open, false, true) == 0, "the capsule must shield the crew");
            context.assertTrue(LifeSupport.radiationRate(earth, earth.getTopPosition(Heightmap.Type.MOTION_BLOCKING,
                    context.getAbsolutePos(BlockPos.ORIGIN)), false, false) == 0, "Earth's magnetic field protects from radiation");
            int noon = LifeSupport.temperature(moon, open, false);
            context.assertTrue(noon > 100, "lunar noon must be scorching, got " + noon);

            earth.setTimeOfDay(18000);
            int midnight = LifeSupport.temperature(moon, open, false);
            context.assertTrue(midnight < -150, "lunar midnight must be freezing, got " + midnight);
            context.assertTrue(midnight >= SpacesuitItem.MIN_TEMPERATURE && noon <= SpacesuitItem.MAX_TEMPERATURE,
                    "the T1 suit must cope with the lunar surface");
            context.assertTrue(LifeSupport.radiationRate(moon, open, false, false) < noonRate, "nights have less radiation");
        } finally {
            earth.setTimeOfDay(time);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void oxygenStationFillsTank(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        context.setBlockState(pos, ModBlocks.OXYGEN_STATION);
        OxygenStationBlockEntity station = (OxygenStationBlockEntity) context.getBlockEntity(pos);
        station.energy.amount = station.energy.capacity;
        station.oxygen.fillMb(2000);
        station.setStack(OxygenStationBlockEntity.FILL, new ItemStack(ModItems.OXYGEN_TANK));
        context.addInstantFinalTask(() -> {
            ItemStack tank = station.getStack(OxygenStationBlockEntity.FILL);
            context.assertTrue(Oxygen.stored(tank) == OxygenTankItem.CAPACITY, "the tank must be filled, has " + Oxygen.stored(tank));
            context.assertTrue(station.oxygen.amountMb() == 2000 - OxygenTankItem.CAPACITY, "filling must use liquid oxygen");
        });
    }
}
