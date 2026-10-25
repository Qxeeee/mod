package com.orbitcore.devtools;

import com.orbitcore.block.HorizontalFacingBlock;
import com.orbitcore.block.MachineBlock;
import com.orbitcore.block.RegolithBlock;
import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.ElectrolyzerBlockEntity;
import com.orbitcore.block.entity.FuelRefineryBlockEntity;
import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import com.orbitcore.block.entity.OxygenStationBlockEntity;
import com.orbitcore.block.entity.RocketAssemblerBlockEntity;
import com.orbitcore.block.entity.SampleAnalyzerBlockEntity;
import com.orbitcore.block.entity.SealerBlockEntity;
import com.orbitcore.client.network.OrbitClientNetworking;
import com.orbitcore.client.render.RocketModelRenderer;
import com.orbitcore.client.screen.MissionControlScreen;
import com.orbitcore.client.screen.RocketScreen;
import com.orbitcore.client.screen.TelescopeScreen;
import com.orbitcore.client.toast.OrbitToast;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.item.OxygenTankItem;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModGameRules;
import com.orbitcore.registry.ModItems;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketPart;
import com.orbitcore.science.CelestialBody;
import com.orbitcore.science.ObservationTarget;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.survival.LifeSupport;
import com.orbitcore.survival.Oxygen;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Фототур для разработки: создаёт плоский мир, расставляет блоки мода, по очереди
 * открывает экраны и сохраняет скриншоты в {@code run/screenshots/tour}, потом закрывает игру.
 * Запуск: {@code gradlew runTour}.
 */
public class ScreenshotTour implements ClientModInitializer {
    private static final String WORLD = "orbitcore-tour";

    private final Deque<Step> steps = new ArrayDeque<>();
    private Phase phase = Phase.LANGUAGE;
    private int wait;
    private BlockPos origin;

    private enum Phase { LANGUAGE, TITLE, LOADING, TOUR }

    private record Step(int waitAfter, Consumer<MinecraftClient> action, Predicate<MinecraftClient> until, int timeout) {
        Step(int waitAfter, Consumer<MinecraftClient> action) {
            this(waitAfter, action, c -> true, 0);
        }
    }

    private int waited;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (wait > 0) {
            wait--;
            return;
        }
        switch (phase) {
            case LANGUAGE -> {
                if (!(client.currentScreen instanceof TitleScreen)) {
                    return;
                }
                if (!"ru_ru".equals(client.options.language)) {
                    client.options.language = "ru_ru";
                    client.getLanguageManager().setLanguage("ru_ru");
                    client.reloadResources();
                    wait = 40;
                }
                phase = Phase.TITLE;
            }
            case TITLE -> {
                if (client.currentScreen instanceof TitleScreen) {
                    createWorld(client);
                    phase = Phase.LOADING;
                }
            }
            case LOADING -> {
                if (client.player != null && client.world != null && client.currentScreen == null && client.getServer() != null) {
                    planTour(client);
                    phase = Phase.TOUR;
                    wait = 40;
                }
            }
            case TOUR -> {
                Step step = steps.peek();
                if (step == null) {
                    return;
                }
                // Шаг-ожидание держит очередь, пока условие не выполнится (или не выйдет время)
                if (!step.until.test(client) && waited++ < step.timeout) {
                    return;
                }
                waited = 0;
                steps.poll();
                step.action.accept(client);
                wait = step.waitAfter;
            }
        }
    }

    private void createWorld(MinecraftClient client) {
        try {
            FileUtils.deleteDirectory(new File(client.runDirectory, "saves/" + WORLD));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete old tour world", e);
        }
        LevelInfo info = new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart(WORLD, info, new GeneratorOptions(42L, false, false),
                registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder());
    }

    // ------------------------------------------------------------------ сценарий

    private void planTour(MinecraftClient client) {
        origin = client.player.getBlockPos();
        // Быстрый режим для проверки неба Луны: файл run/tour-moon-only
        if (new File(client.runDirectory, "tour-moon-only").exists()) {
            planMoonSky(client);
            step(20, c -> c.scheduleStop());
            return;
        }
        // Быстрый режим для этапа 0.7: файл run/tour-survival-only
        if (new File(client.runDirectory, "tour-survival-only").exists()) {
            planMoonSky(client);
            planSurvival(client);
            step(20, c -> c.scheduleStop());
            return;
        }
        BlockPos row = origin.south(4);
        BlockPos furnace = row.west(3);
        BlockPos table = row.west(2);
        BlockPos analyzer = row.west(1);
        BlockPos generator = row;
        BlockPos electrolyzer = row.east(1);
        BlockPos telescope = row.east(2);
        BlockPos missionControl = row.east(3);

        server(client, (server, world, player) -> {
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            world.setWeather(12000, 0, false, false);
            world.setTimeOfDay(1000);
            place(world, furnace, ModBlocks.METALLURGICAL_FURNACE);
            place(world, table, ModBlocks.ENGINEERING_TABLE);
            place(world, analyzer, ModBlocks.SAMPLE_ANALYZER);
            place(world, generator, ModBlocks.COAL_GENERATOR);
            place(world, electrolyzer, ModBlocks.ELECTROLYZER);
            place(world, telescope, ModBlocks.TELESCOPE);
            place(world, missionControl, ModBlocks.MISSION_CONTROL);

            var f = (MetallurgicalFurnaceBlockEntity) world.getBlockEntity(furnace);
            f.setStack(MetallurgicalFurnaceBlockEntity.INPUT, new ItemStack(Items.IRON_INGOT, 8));
            f.setStack(MetallurgicalFurnaceBlockEntity.REDUCTANT, new ItemStack(Items.COAL, 8));
            f.setStack(MetallurgicalFurnaceBlockEntity.FUEL, new ItemStack(Items.COAL, 16));
            ((CoalGeneratorBlockEntity) world.getBlockEntity(generator)).setStack(CoalGeneratorBlockEntity.FUEL, new ItemStack(Items.COAL, 16));
            ((ElectrolyzerBlockEntity) world.getBlockEntity(electrolyzer)).setStack(ElectrolyzerBlockEntity.INPUT, new ItemStack(ModItems.RAW_BAUXITE, 16));
            ((SampleAnalyzerBlockEntity) world.getBlockEntity(analyzer)).setStack(SampleAnalyzerBlockEntity.INPUT, new ItemStack(ModItems.RAW_RUTILE, 3));

            SpaceProgram program = SpaceProgram.get(server);
            program.addPoints(server, ScienceType.OBSERVATION, 12);
            program.addPoints(server, ScienceType.SAMPLE, 4);
            player.teleport(world, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0f, 18f);
        }, 160);

        step(0, c -> shot(c, "01_machines_day"));
        server(client, (server, world, player) -> world.setTimeOfDay(18000), 40);
        step(0, c -> shot(c, "02_machines_night"));

        step(25, c -> c.setScreen(new TelescopeScreen(telescope)));
        step(0, c -> shot(c, "03_telescope_search"));
        step(25, c -> ((TelescopeScreen) c.currentScreen).aimAt(ObservationTarget.MOON, c.world.getTimeOfDay(), 3.0f));
        step(0, c -> shot(c, "04_telescope_moon"));
        step(25, c -> ((TelescopeScreen) c.currentScreen).aimAt(ObservationTarget.ORION, c.world.getTimeOfDay(), 1.3f));
        step(0, c -> shot(c, "05_telescope_orion"));

        step(15, c -> c.setScreen(new MissionControlScreen(missionControl)));
        step(5, c -> clickResearchNode(c, 1, 0));
        step(0, c -> shot(c, "06_mission_control_research"));
        step(10, c -> ((MissionControlScreen) c.currentScreen).showTab(1));
        step(0, c -> shot(c, "07_mission_control_bodies"));
        step(10, c -> ((MissionControlScreen) c.currentScreen).showTab(2));
        step(0, c -> shot(c, "08_mission_control_cosmopedia"));

        step(5, c -> c.setScreen(null));
        server(client, (server, world, player) -> SpaceProgram.get(server).discover(server, CelestialBody.MOON), 0);
        step(30, c -> c.getToastManager().add(OrbitToast.discovery(Text.translatable(CelestialBody.MOON.translationKey()), true)));
        step(0, c -> shot(c, "09_discovery_toast"));

        openMachine(client, furnace);
        step(0, c -> shot(c, "10_furnace"));
        openMachine(client, analyzer);
        step(0, c -> shot(c, "11_analyzer"));
        openMachine(client, generator);
        step(0, c -> shot(c, "12_generator"));
        openMachine(client, electrolyzer);
        step(0, c -> shot(c, "13_electrolyzer"));
        step(5, c -> c.setScreen(null));
        planRocket(client);
        step(20, c -> c.scheduleStop());
    }

    /** Этап 0.4: стол с лунной ракетой за рядом машин, стенд, экран ракеты и машины топлива. */
    private void planRocket(MinecraftClient client) {
        BlockPos pad = origin.south(12);
        BlockPos assembler = pad.add(-4, 0, -2);
        BlockPos refinery = pad.add(4, 0, -2);
        BlockPos separator = pad.add(5, 0, -1);
        BlockPos refineryPower = pad.add(4, 0, -1);
        int[] rocketId = new int[1];

        server(client, (server, world, player) -> {
            world.setTimeOfDay(5000);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(pad.add(dx, -1, dz), ModBlocks.LAUNCH_PAD.getDefaultState());
                }
            }
            RocketEntity rocket = ModEntities.ROCKET.create(world);
            rocket.refreshPositionAndAngles(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, 0, 0);
            RocketDesign design = new RocketDesign(MOON_ROCKET);
            rocket.setDesign(design);
            rocket.setFuel(design.propellantCapacity(), design.propellantCapacity() - 3000);
            world.spawnEntity(rocket);
            rocketId[0] = rocket.getId();

            place(world, assembler, ModBlocks.ROCKET_ASSEMBLER);
            var stand = (RocketAssemblerBlockEntity) world.getBlockEntity(assembler);
            for (int i = 0; i < MOON_ROCKET.size(); i++) {
                RocketDesign.Entry e = MOON_ROCKET.get(i);
                stand.setStack(i, new ItemStack(RocketModelRenderer.itemFor(e.part()), e.count()));
            }
            place(world, refinery, ModBlocks.FUEL_REFINERY);
            place(world, separator, ModBlocks.AIR_SEPARATOR);
            place(world, refineryPower, ModBlocks.COAL_GENERATOR);
            ((CoalGeneratorBlockEntity) world.getBlockEntity(refineryPower)).setStack(CoalGeneratorBlockEntity.FUEL, new ItemStack(Items.COAL, 16));
            var r = (FuelRefineryBlockEntity) world.getBlockEntity(refinery);
            r.setStack(FuelRefineryBlockEntity.COAL, new ItemStack(Items.COAL, 16));
            r.setStack(FuelRefineryBlockEntity.WATER_IN, new ItemStack(Items.WATER_BUCKET));

            Item[] hotbar = {ModItems.ENGINE_ISKRA, ModItems.ENGINE_FAKEL, ModItems.FUEL_TANK_SMALL, ModItems.FUEL_TANK_LARGE,
                    ModItems.STAGE_DECOUPLER, ModItems.CAPSULE_KAPLYA, ModItems.ROCKET_FINS, ModItems.LANDING_LEGS, ModFluids.KEROSENE_BUCKET};
            for (int i = 0; i < hotbar.length; i++) {
                player.getInventory().setStack(i, new ItemStack(hotbar[i]));
            }
            player.teleport(world, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0f, -16f);
        }, 120);
        step(0, c -> shot(c, "14_rocket_on_pad"));
        server(client, (server, world, player) ->
                player.teleport(world, pad.getX() + 4.5, pad.getY() + 1, pad.getZ() - 5.5, 35f, -30f), 40);
        step(0, c -> shot(c, "15_rocket_close"));

        server(client, (server, world, player) ->
                player.teleport(world, assembler.getX() + 0.5, assembler.getY(), assembler.getZ() - 2.5, 0f, 20f), 20);
        openMachine(client, assembler);
        step(0, c -> shot(c, "16_rocket_assembler"));
        step(10, c -> c.setScreen(new RocketScreen(rocketId[0])));
        step(0, c -> shot(c, "17_rocket_screen"));
        server(client, (server, world, player) ->
                player.teleport(world, refinery.getX() + 0.5, refinery.getY(), refinery.getZ() - 2.5, 0f, 20f), 20);
        openMachine(client, refinery);
        step(0, c -> shot(c, "18_fuel_refinery"));
        openMachine(client, separator);
        step(0, c -> shot(c, "19_air_separator"));
        step(5, c -> c.setScreen(null));
        planFlight(client, rocketId);
    }

    /** Этап 0.5: полёт лунной ракеты на орбиту и обратно, кадры кинокамеры. */
    private void planFlight(MinecraftClient client, int[] rocketId) {
        server(client, (server, world, player) -> {
            if (world.getEntityById(rocketId[0]) instanceof RocketEntity rocket) {
                rocket.setFuel(rocket.design().propellantCapacity(), rocket.design().propellantCapacity());
                player.teleport(world, rocket.getX() + 2, rocket.getY(), rocket.getZ() - 2, 0, 0);
                FlightController.launch(rocket, player, RocketDesign.Destination.EARTH_ORBIT);
            }
        }, 60);
        step(100, c -> shot(c, "20_countdown_wide"));
        step(70, c -> shot(c, "21_ignition"));
        step(80, c -> shot(c, "22_liftoff"));
        step(0, c -> shot(c, "23_from_below"));
        waitFor(c -> {
            RocketEntity r = rocketById(c, rocketId[0]);
            return r != null && r.separationTick() > 0;
        }, 800);
        step(20, c -> {
        });
        step(60, c -> shot(c, "24_separation"));
        step(0, c -> shot(c, "25_chase"));
        waitFor(c -> {
            RocketEntity r = rocketById(c, rocketId[0]);
            return r != null && r.phase() == FlightPhase.SPACE;
        }, 900);
        step(40, c -> {
        });
        step(5, c -> shot(c, "26_orbit"));
        step(0, c -> OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_MANEUVER, 0));
        waitFor(c -> {
            RocketEntity r = rocketById(c, rocketId[0]);
            return r != null && r.phase() == FlightPhase.REENTRY;
        }, 200);
        step(40, c -> {
        });
        step(0, c -> shot(c, "27_reentry"));
        waitFor(c -> {
            RocketEntity r = rocketById(c, rocketId[0]);
            return r != null && r.parachute();
        }, 600);
        step(40, c -> {
        });
        step(0, c -> shot(c, "28_parachute"));
        waitFor(c -> c.player.getVehicle() == null, 900);
        step(30, c -> {
        });
        step(0, c -> shot(c, "29_landed"));
        planMoon(client);
    }

    /** Сразу на Луну: небо днём и ночью. */
    private void planMoonSky(MinecraftClient client) {
        server(client, (server, world, player) -> {
            ServerWorld moon = server.getWorld(ModDimensions.MOON);
            moon.getChunk(0, 0);
            int ground = moon.getTopY(Heightmap.Type.MOTION_BLOCKING, 8, 8);
            world.setTimeOfDay(6000);
            FabricDimensions.teleport(player, moon, new TeleportTarget(new Vec3d(8.5, ground + 1, 8.5), Vec3d.ZERO, -145f, -32f));
        }, 120);
        step(0, c -> shot(c, "moon_sky_day"));
        server(client, (server, world, player) -> world.setTimeOfDay(18000), 40);
        step(0, c -> shot(c, "moon_sky_night"));
        server(client, (server, world, player) ->
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), 60f, -60f), 30);
        step(0, c -> shot(c, "moon_sky_stars"));
    }

    /** Этап 0.6: полёт на Луну на автопилоте, лунный пейзаж, Земля в небе, ночь, следы. */
    private void planMoon(MinecraftClient client) {
        BlockPos pad = origin.south(12);
        server(client, (server, world, player) -> {
            server.getGameRules().get(ModGameRules.ENGINEER_MODE).set(false, server);
            world.setTimeOfDay(5000);
            RocketEntity rocket = ModEntities.ROCKET.create(world);
            rocket.refreshPositionAndAngles(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, 0, 0);
            RocketDesign design = new RocketDesign(MOON_ROCKET);
            rocket.setDesign(design);
            rocket.setFuel(design.propellantCapacity(), design.propellantCapacity());
            world.spawnEntity(rocket);
            player.teleport(world, pad.getX() + 2.5, pad.getY(), pad.getZ() - 2.5, 0, 0);
            FlightController.launch(rocket, player, RocketDesign.Destination.MOON_LANDING);
        }, 30);
        step(20, c -> OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_SKIP, 0));
        step(5, c -> shot(c, "30_orbit_moon"));
        step(120, c -> OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_MANEUVER, 0));
        step(5, c -> shot(c, "31_transfer"));
        step(0, c -> OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_SKIP, 0));
        waitFor(c -> c.player.getVehicle() instanceof RocketEntity r && r.phase() == FlightPhase.DESCENT, 400);
        step(50, c -> {
        });
        step(0, c -> shot(c, "32_descent"));
        waitFor(c -> c.player.getVehicle() == null && c.world.getRegistryKey() == ModDimensions.MOON, 1200);
        step(40, c -> {
        });
        step(0, c -> shot(c, "33_moon_landed"));
        server(client, (server, world, player) -> {
            ServerWorld moon = server.getWorld(ModDimensions.MOON);
            BlockPos at = player.getBlockPos();
            // Цепочка следов от ракеты
            for (int i = 1; i < 9; i++) {
                BlockPos step = moon.getTopPosition(Heightmap.Type.MOTION_BLOCKING, at.add(i, 0, i % 2)).down();
                BlockState state = moon.getBlockState(step);
                if (state.getBlock() instanceof RegolithBlock) {
                    moon.setBlockState(step, state.with(RegolithBlock.TRODDEN, true));
                }
            }
            player.networkHandler.requestTeleport(player.getX() - 3, player.getY() + 1, player.getZ() - 3, -45f, 30f);
        }, 30);
        step(0, c -> shot(c, "34_footprints"));
        server(client, (server, world, player) ->
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), -145f, -32f), 30);
        step(0, c -> shot(c, "35_earth_in_moon_sky"));
        server(client, (server, world, player) ->
                player.networkHandler.requestTeleport(player.getX(), player.getY() + 12, player.getZ(), 20f, 12f), 40);
        step(0, c -> shot(c, "36_moon_landscape"));
        server(client, (server, world, player) -> {
            world.setTimeOfDay(18000);
            player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), -145f, -25f);
        }, 40);
        step(0, c -> shot(c, "37_moon_night"));
        server(client, (server, world, player) -> server.getGameRules().get(ModGameRules.ENGINEER_MODE).set(true, server), 5);
        planSurvival(client);
    }

    /**
     * Этап 0.7: скафандр и его HUD, база с герметизатором и шлюзом,
     * утечка воздуха, кислородная станция и предупреждение о вакууме.
     */
    private void planSurvival(MinecraftClient client) {
        BlockPos[] base = new BlockPos[1];
        server(client, (server, world, player) -> {
            ServerWorld moon = server.getWorld(ModDimensions.MOON);
            world.setTimeOfDay(4000);
            BlockPos at = player.getBlockPos();
            BlockPos corner = moon.getTopPosition(Heightmap.Type.MOTION_BLOCKING, at.add(6, 0, -3));
            base[0] = corner;
            buildBase(moon, corner);
            player.getInventory().clear();
            player.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACESUIT_HELMET));
            ItemStack chest = new ItemStack(ModItems.SPACESUIT_CHESTPLATE);
            Oxygen.set(chest, 472);
            player.equipStack(EquipmentSlot.CHEST, chest);
            player.equipStack(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACESUIT_LEGGINGS));
            player.equipStack(EquipmentSlot.FEET, new ItemStack(ModItems.SPACESUIT_BOOTS));
            ItemStack tank = new ItemStack(ModItems.OXYGEN_TANK);
            Oxygen.set(tank, OxygenTankItem.CAPACITY);
            player.getInventory().setStack(1, tank);
            player.getInventory().setStack(0, new ItemStack(ModItems.DOSIMETER));
            player.getInventory().selectedSlot = 0;
            LifeSupport.get(player).dose = 37;
            player.networkHandler.requestTeleport(corner.getX() - 6.5, corner.getY() + 1, corner.getZ() + 3.5, -70f, 8f);
        }, 80);
        step(0, c -> shot(c, "38_suit_hud"));
        step(20, c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        step(0, c -> shot(c, "39_spacesuit"));
        step(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        server(client, (server, world, player) ->
                player.networkHandler.requestTeleport(base[0].getX() - 6.5, base[0].getY(), base[0].getZ() - 6.5, -45f, 6f), 40);
        step(0, c -> shot(c, "40_moon_base"));
        openMachineOn(client, ModDimensions.MOON, () -> base[0].add(3, 1, 0), () -> Vec3d.of(base[0]).add(3.5, 1, 2.5));
        step(0, c -> shot(c, "41_sealer"));
        openMachineOn(client, ModDimensions.MOON, () -> base[0].add(5, 1, 3), () -> Vec3d.of(base[0]).add(3.5, 1, 3.5));
        step(0, c -> shot(c, "42_oxygen_station"));

        // Утечка: выбитое окно, струя пара и окно герметизатора с координатами дыры
        step(5, c -> c.setScreen(null));
        server(client, (server, world, player) -> {
            ServerWorld moon = server.getWorld(ModDimensions.MOON);
            BlockPos window = base[0].add(2, 2, 6);
            moon.setBlockState(window, Blocks.AIR.getDefaultState());
            ((SealerBlockEntity) moon.getBlockEntity(base[0].add(3, 1, 0))).rescan(moon, base[0].add(3, 1, 0));
            player.networkHandler.requestTeleport(window.getX() + 0.5, window.getY() - 1, window.getZ() + 4.5, 180f, 10f);
        }, 50);
        step(0, c -> shot(c, "43_air_leak"));
        openMachineOn(client, ModDimensions.MOON, () -> base[0].add(3, 1, 0), () -> Vec3d.of(base[0]).add(3.5, 1, 2.5));
        step(0, c -> shot(c, "44_sealer_leak"));

        // Заделали окно — внутри снова воздух и тепло
        step(5, c -> c.setScreen(null));
        server(client, (server, world, player) -> {
            ServerWorld moon = server.getWorld(ModDimensions.MOON);
            moon.setBlockState(base[0].add(2, 2, 6), Blocks.GLASS.getDefaultState());
            ((SealerBlockEntity) moon.getBlockEntity(base[0].add(3, 1, 0))).rescan(moon, base[0].add(3, 1, 0));
            player.networkHandler.requestTeleport(base[0].getX() + 1.5, base[0].getY() + 1, base[0].getZ() + 3.5, -90f, 15f);
        }, 50);
        step(0, c -> shot(c, "45_inside_base"));

        // Без скафандра снаружи: предупреждение о вакууме и тающий запас воздуха
        server(client, (server, world, player) -> {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                player.equipStack(slot, ItemStack.EMPTY);
            }
            player.changeGameMode(GameMode.SURVIVAL);
            player.networkHandler.requestTeleport(base[0].getX() - 6.5, base[0].getY() + 1, base[0].getZ() + 3.5, -70f, 8f);
        }, 120);
        step(0, c -> shot(c, "46_vacuum_warning"));
        server(client, (server, world, player) -> {
            player.changeGameMode(GameMode.CREATIVE);
            player.setAir(player.getMaxAir());
        }, 5);
    }

    /**
     * Маленькая база 7×5×7: стальные стены с окнами, шлюзовая дверь на западе,
     * герметизатор в северной стене решёткой внутрь, кислородная станция у восточной.
     */
    private static void buildBase(ServerWorld moon, BlockPos corner) {
        for (BlockPos pos : BlockPos.iterate(corner.add(-8, 0, -8), corner.add(14, 12, 14))) {
            moon.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        for (BlockPos pos : BlockPos.iterate(corner.add(-8, -1, -8), corner.add(14, -1, 14))) {
            moon.setBlockState(pos, ModBlocks.REGOLITH.getDefaultState());
        }
        for (BlockPos pos : BlockPos.iterate(corner, corner.add(6, 4, 6))) {
            int x = pos.getX() - corner.getX();
            int y = pos.getY() - corner.getY();
            int z = pos.getZ() - corner.getZ();
            boolean wall = x == 0 || x == 6 || y == 0 || y == 4 || z == 0 || z == 6;
            if (!wall) {
                continue;
            }
            boolean window = y == 2 && (x == 2 || x == 4) && (z == 0 || z == 6);
            moon.setBlockState(pos, window ? Blocks.GLASS.getDefaultState() : ModBlocks.STEEL_BLOCK.getDefaultState());
        }
        moon.setBlockState(corner.add(3, 4, 3), Blocks.SEA_LANTERN.getDefaultState());
        BlockState door = ModBlocks.AIRLOCK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.EAST);
        moon.setBlockState(corner.add(0, 1, 3), door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        moon.setBlockState(corner.add(0, 2, 3), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        BlockPos sealerPos = corner.add(3, 1, 0);
        moon.setBlockState(sealerPos, ModBlocks.SEALER.getDefaultState().with(MachineBlock.FACING, Direction.SOUTH));
        SealerBlockEntity sealer = (SealerBlockEntity) moon.getBlockEntity(sealerPos);
        sealer.energy.amount = sealer.energy.capacity;
        sealer.oxygen.fillMb(3000);
        sealer.rescan(moon, sealerPos);

        BlockPos stationPos = corner.add(5, 1, 3);
        moon.setBlockState(stationPos, ModBlocks.OXYGEN_STATION.getDefaultState().with(MachineBlock.FACING, Direction.WEST));
        OxygenStationBlockEntity station = (OxygenStationBlockEntity) moon.getBlockEntity(stationPos);
        station.energy.amount = station.energy.capacity / 2;
        station.oxygen.fillMb(5200);
        ItemStack tank = new ItemStack(ModItems.OXYGEN_TANK);
        Oxygen.set(tank, 540);
        station.setStack(OxygenStationBlockEntity.FILL, tank);
        station.setStack(OxygenStationBlockEntity.BUCKET_IN, new ItemStack(ModFluids.LIQUID_OXYGEN_BUCKET));
    }

    /** Открыть машину в другом мире; окно закрывается, если игрок дальше 8 блоков, поэтому сначала подходим. */
    private void openMachineOn(MinecraftClient client, RegistryKey<World> dimension, Supplier<BlockPos> pos, Supplier<Vec3d> stand) {
        step(5, c -> c.setScreen(null));
        server(client, (server, world, player) ->
                player.networkHandler.requestTeleport(stand.get().x, stand.get().y, stand.get().z, player.getYaw(), player.getPitch()), 10);
        server(client, (server, world, player) ->
                player.openHandledScreen((NamedScreenHandlerFactory) server.getWorld(dimension).getBlockEntity(pos.get())), 25);
    }

    private static final List<RocketDesign.Entry> MOON_ROCKET = List.of(
            new RocketDesign.Entry(RocketPart.ENGINE_FAKEL, 2),
            new RocketDesign.Entry(RocketPart.FINS, 1),
            new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1),
            new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1),
            new RocketDesign.Entry(RocketPart.STAGE_DECOUPLER, 1),
            new RocketDesign.Entry(RocketPart.ENGINE_ISKRA, 1),
            new RocketDesign.Entry(RocketPart.FUEL_TANK_LARGE, 1),
            new RocketDesign.Entry(RocketPart.LANDING_LEGS, 1),
            new RocketDesign.Entry(RocketPart.CAPSULE, 1));

    // ------------------------------------------------------------------ помощники

    private interface ServerAction {
        void run(MinecraftServer server, ServerWorld world, ServerPlayerEntity player);
    }

    private void step(int waitAfter, Consumer<MinecraftClient> action) {
        steps.add(new Step(waitAfter, action));
    }

    /** Ждать (не дольше timeout тактов), пока условие не станет истинным. */
    private void waitFor(Predicate<MinecraftClient> condition, int timeout) {
        steps.add(new Step(0, c -> {
        }, condition, timeout));
    }

    private static RocketEntity rocketById(MinecraftClient client, int id) {
        return client.world != null && client.world.getEntityById(id) instanceof RocketEntity r ? r : null;
    }

    private void server(MinecraftClient client, ServerAction action, int waitAfter) {
        step(waitAfter, c -> {
            MinecraftServer server = c.getServer();
            server.execute(() -> action.run(server, server.getOverworld(),
                    server.getPlayerManager().getPlayer(c.player.getUuid())));
        });
    }

    private void openMachine(MinecraftClient client, BlockPos pos) {
        step(5, c -> c.setScreen(null));
        server(client, (server, world, player) -> player.openHandledScreen((NamedScreenHandlerFactory) world.getBlockEntity(pos)), 25);
    }

    private static void place(ServerWorld world, BlockPos pos, Block block) {
        BlockState state = block.getDefaultState();
        if (state.contains(MachineBlock.FACING)) {
            state = state.with(MachineBlock.FACING, Direction.NORTH);
        } else if (state.contains(HorizontalFacingBlock.FACING)) {
            state = state.with(HorizontalFacingBlock.FACING, Direction.NORTH);
        }
        world.setBlockState(pos, state);
    }

    private static void clickResearchNode(MinecraftClient client, int x, int y) {
        int left = (client.currentScreen.width - 276) / 2;
        int top = (client.currentScreen.height - 206) / 2;
        client.currentScreen.mouseClicked(left + 20 + x * 62 + 12, top + 62 + y * 34 + 12, 0);
    }

    private static void shot(MinecraftClient client, String name) {
        new File(client.runDirectory, "screenshots/tour").mkdirs();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "tour/" + name + ".png", client.getFramebuffer(), message -> {
        });
    }
}
