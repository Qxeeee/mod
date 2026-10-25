package com.orbitcore.devtools;

import com.orbitcore.block.HorizontalFacingBlock;
import com.orbitcore.block.MachineBlock;
import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.ElectrolyzerBlockEntity;
import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import com.orbitcore.block.entity.SampleAnalyzerBlockEntity;
import com.orbitcore.client.screen.MissionControlScreen;
import com.orbitcore.client.screen.TelescopeScreen;
import com.orbitcore.client.toast.OrbitToast;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModItems;
import com.orbitcore.science.CelestialBody;
import com.orbitcore.science.ObservationTarget;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

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

    private record Step(int waitAfter, Consumer<MinecraftClient> action) {
    }

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
                Step step = steps.poll();
                if (step != null) {
                    step.action.accept(client);
                    wait = step.waitAfter;
                }
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
        step(5, c -> clickResearchNode(c, 2, 0));
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
        step(20, c -> c.scheduleStop());
    }

    // ------------------------------------------------------------------ помощники

    private interface ServerAction {
        void run(MinecraftServer server, ServerWorld world, ServerPlayerEntity player);
    }

    private void step(int waitAfter, Consumer<MinecraftClient> action) {
        steps.add(new Step(waitAfter, action));
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
        client.currentScreen.mouseClicked(left + 20 + x * 46 + 12, top + 62 + y * 34 + 12, 0);
    }

    private static void shot(MinecraftClient client, String name) {
        new File(client.runDirectory, "screenshots/tour").mkdirs();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "tour/" + name + ".png", client.getFramebuffer(), message -> {
        });
    }
}
