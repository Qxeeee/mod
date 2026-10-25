package com.orbitcore.rocket;

import com.orbitcore.block.LaunchPadBlock;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.entity.RocketStageEntity;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModGameRules;
import com.orbitcore.rocket.RocketDesign.Destination;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import net.minecraft.entity.MovementType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Полёт ракеты на сервере: отсчёт, подъём с отделением ступеней, орбита,
 * вход в атмосферу и посадка капсулы на парашюте. Клиенту уходят только этап,
 * такт и несколько чисел — по ним он сам ставит камеру, субтитры и эффекты.
 */
public final class FlightController {
    public static final int COUNTDOWN_TICKS = 200;
    public static final int IGNITION_TICK = 140;
    /** Высота над столом, на которой начинается космос. */
    public static final double SPACE_ALTITUDE = 420;
    public static final int ORBIT_TICKS = 700;
    public static final double REENTRY_ALTITUDE = 380;
    public static final double PARACHUTE_HEIGHT = 60;
    /** Сколько дельта-V уходит на выход в космос; остальное — на дальнейший путь. */
    public static final int ORBIT_DELTA_V = 3000;

    /** Куда уже можно лететь. Луна появится вместе с её миром. */
    private static final Set<Destination> AVAILABLE = EnumSet.of(Destination.SUBORBITAL, Destination.EARTH_ORBIT);

    private final RocketEntity rocket;
    private final UUID crew;
    private final Destination destination;
    private final List<Double> separations = new ArrayList<>();
    private double velX;
    private double velY;
    private int separated;
    private boolean deorbit;
    private int crewMissing;

    private FlightController(RocketEntity rocket, UUID crew, Destination destination) {
        this.rocket = rocket;
        this.crew = crew;
        this.destination = destination;
    }

    public static boolean isAvailable(Destination destination) {
        return AVAILABLE.contains(destination);
    }

    /** Почему ракета не может лететь туда сейчас, или null, если может. */
    @Nullable
    public static Text whyCannotLaunch(RocketEntity rocket, ServerPlayerEntity player, Destination destination) {
        World world = rocket.getWorld();
        RocketDesign design = rocket.design();
        if (rocket.phase() != FlightPhase.IDLE) {
            return Text.translatable("message.orbitcore.flight.busy");
        }
        if (!isAvailable(destination)) {
            return Text.translatable("message.orbitcore.flight.not_yet");
        }
        if (world.getRegistryKey() != World.OVERWORLD) {
            return Text.translatable("message.orbitcore.flight.wrong_world");
        }
        if (!LaunchPadBlock.isComplete(world, rocket.getBlockPos().down())) {
            return Text.translatable("message.orbitcore.rocket.pad_incomplete");
        }
        if (!design.isValid()) {
            return Text.translatable("message.orbitcore.flight.bad_design");
        }
        if (!design.has(RocketPart.Kind.CAPSULE)) {
            return Text.translatable("rocket.orbitcore.problem.no_capsule");
        }
        if (design.liftoffTwr(rocket.fill()) <= 1.0) {
            return Text.translatable("rocket.orbitcore.problem.low_thrust");
        }
        double needed = destination.deltaV() * (engineerMode(world) ? 1.0 : 0.9);
        if (design.deltaV(rocket.fill()) < needed) {
            return Text.translatable("message.orbitcore.flight.not_enough_fuel", (int) design.deltaV(rocket.fill()), destination.deltaV());
        }
        int top = (int) Math.ceil(rocket.getY() + design.height());
        if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, rocket.getBlockX(), rocket.getBlockZ()) > top) {
            return Text.translatable("message.orbitcore.flight.blocked");
        }
        if (player.hasVehicle()) {
            return Text.translatable("message.orbitcore.flight.busy");
        }
        return null;
    }

    public static boolean engineerMode(World world) {
        return world.getGameRules().getBoolean(ModGameRules.ENGINEER_MODE);
    }

    /** Экипаж садится в капсулу, начинается отсчёт. */
    public static void launch(RocketEntity rocket, ServerPlayerEntity player, Destination destination) {
        FlightController flight = new FlightController(rocket, player.getUuid(), destination);
        double fill = rocket.fill();
        // Ступени, которые выгорят ещё до космоса, отделятся на подъёме
        int needed = destination == Destination.SUBORBITAL ? Destination.SUBORBITAL.deltaV() : ORBIT_DELTA_V;
        List<Double> stageDv = rocket.design().stageDeltaV(fill);
        double sum = 0;
        for (int i = 0; i < stageDv.size() - 1; i++) {
            sum += stageDv.get(i);
            if (sum >= needed) {
                break;
            }
            flight.separations.add(sum / needed);
        }
        rocket.setFlight(flight);
        rocket.setDestination(destination);
        rocket.setBaseY((float) rocket.getY());
        rocket.setSeparationTick(-1000);
        rocket.setPhase(FlightPhase.COUNTDOWN);
        player.startRiding(rocket, true);
        rocket.getWorld().playSound(null, rocket.getBlockPos(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.NEUTRAL, 1.0f, 0.6f);
    }

    /** Полёт, прерванный перезапуском сервера: капсула сразу садится там, где была. */
    public static void recover(RocketEntity rocket) {
        FlightController flight = new FlightController(rocket, new UUID(0, 0), Destination.SUBORBITAL);
        flight.crewMissing = 1000;
        rocket.setFlight(flight);
    }

    // ------------------------------------------------------------------ команды экипажа

    /** Пропустить подъём: ракета сразу оказывается в космосе. */
    public void skip() {
        FlightPhase phase = rocket.phase();
        if (phase != FlightPhase.COUNTDOWN && phase != FlightPhase.ASCENT) {
            return;
        }
        while (separated < separations.size()) {
            separate(false);
        }
        rocket.setPosition(rocket.getX() + velX * 40, rocket.baseY() + SPACE_ALTITUDE, rocket.getZ());
        reachSpace();
    }

    public void requestDeorbit() {
        deorbit = true;
    }

    // ------------------------------------------------------------------ тик

    public void tick() {
        ServerWorld world = (ServerWorld) rocket.getWorld();
        if (!checkCrew(world)) {
            return;
        }
        int tick = rocket.flightTick() + 1;
        rocket.setFlightTick(tick);
        switch (rocket.phase()) {
            case COUNTDOWN -> countdown(world, tick);
            case ASCENT -> ascent(world, tick);
            case SPACE -> {
                if (deorbit || tick >= ORBIT_TICKS) {
                    startReentry(world);
                }
            }
            case REENTRY -> reentry(world, tick);
            default -> {
            }
        }
    }

    /** Экипаж на месте? Если игрок вышел из игры — полёт заканчивается мягкой посадкой. */
    private boolean checkCrew(ServerWorld world) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(crew);
        if (player == null || player.isDead()) {
            if (++crewMissing > 40) {
                if (rocket.phase() == FlightPhase.COUNTDOWN) {
                    rocket.setPhase(FlightPhase.IDLE);
                    rocket.setFlight(null);
                } else {
                    emergencyLanding(world);
                }
                return false;
            }
            return true;
        }
        crewMissing = 0;
        if (player.getVehicle() != rocket) {
            player.startRiding(rocket, true);
        }
        return true;
    }

    private void countdown(ServerWorld world, int tick) {
        if (tick % 20 == 0 && tick < COUNTDOWN_TICKS) {
            sound(world, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 1.0f, tick >= IGNITION_TICK ? 1.4f : 1.0f);
        }
        if (tick == IGNITION_TICK) {
            sound(world, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
            sound(world, SoundEvents.ITEM_FIRECHARGE_USE, 2.0f, 0.5f);
        }
        if (tick >= COUNTDOWN_TICKS) {
            rocket.setFuel(0, 0);
            rocket.setPhase(FlightPhase.ASCENT);
            sound(world, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.5f);
        }
    }

    private void ascent(ServerWorld world, int tick) {
        double accel = 0.005 + 0.004 * separated;
        velY = Math.min(velY + accel, 3.2);
        if (tick > 160) {
            // Разворот по тангажу: ракета уходит на восток, как настоящие
            velX = Math.min(velX + 0.0012, 0.6);
        }
        rocket.setPosition(rocket.getX() + velX, rocket.getY() + velY, rocket.getZ());
        if (tick % 30 == 0 && tick < 400) {
            sound(world, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 0.4f);
        }
        double progress = (rocket.getY() - rocket.baseY()) / SPACE_ALTITUDE;
        if (separated < separations.size() && progress >= separations.get(separated)) {
            separate(true);
        }
        if (progress >= 1.0) {
            reachSpace();
        }
    }

    /** Отделение отработавшей ступени: нижняя часть падает, ракета продолжает путь. */
    private void separate(boolean withDebris) {
        List<RocketDesign.Entry> parts = rocket.design().parts();
        int cut = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).part().kind() == RocketPart.Kind.DECOUPLER) {
                cut = i;
                break;
            }
        }
        separated++;
        if (cut < 0) {
            return;
        }
        RocketDesign lower = new RocketDesign(parts.subList(0, cut + 1));
        RocketDesign upper = new RocketDesign(parts.subList(cut + 1, parts.size()));
        double drop = lower.height();
        if (withDebris) {
            RocketStageEntity stage = ModEntities.ROCKET_STAGE.create(rocket.getWorld());
            if (stage != null) {
                stage.refreshPositionAndAngles(rocket.getX(), rocket.getY(), rocket.getZ(), 0, 0);
                stage.setDesign(lower);
                stage.setVelocity(velX * 0.6, velY * 0.4, 0.05);
                rocket.getWorld().spawnEntity(stage);
            }
            sound((ServerWorld) rocket.getWorld(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 3.0f, 0.5f);
        }
        rocket.setDesign(upper);
        rocket.setPosition(rocket.getX(), rocket.getY() + drop, rocket.getZ());
        rocket.setSeparationTick(rocket.flightTick());
    }

    private void reachSpace() {
        ServerWorld world = (ServerWorld) rocket.getWorld();
        reward(world, destination == Destination.SUBORBITAL ? 4 : 10);
        if (destination == Destination.SUBORBITAL) {
            startReentry(world);
        } else {
            rocket.setPhase(FlightPhase.SPACE);
            velX = 0;
            velY = 0;
        }
    }

    private void reward(ServerWorld world, int telemetry) {
        MinecraftServer server = world.getServer();
        SpaceProgram.get(server).addPoints(server, ScienceType.TELEMETRY, telemetry);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(crew);
        if (player != null) {
            player.sendMessage(Text.translatable("message.orbitcore.science_gained", telemetry,
                    Text.translatable(ScienceType.TELEMETRY.translationKey())).formatted(Formatting.AQUA), true);
        }
    }

    /** Капсула отделяется от остатков ракеты и входит в атмосферу недалеко от старта. */
    private void startReentry(ServerWorld world) {
        List<RocketDesign.Entry> capsule = rocket.design().parts().stream()
                .filter(e -> e.part().kind() == RocketPart.Kind.CAPSULE)
                .toList();
        rocket.setDesign(new RocketDesign(capsule));
        double landX = Math.floor(rocket.getX() - velX * 30) + 0.5 + (world.random.nextDouble() - 0.5) * 40;
        double landZ = Math.floor(rocket.getZ()) + 0.5 + (world.random.nextDouble() - 0.5) * 40;
        rocket.refreshPositionAndAngles(landX, rocket.baseY() + REENTRY_ALTITUDE, landZ, 0, 0);
        velX = 0;
        velY = -1.0;
        rocket.setParachute(false);
        rocket.setPhase(FlightPhase.REENTRY);
    }

    private void reentry(ServerWorld world, int tick) {
        int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, rocket.getBlockX(), rocket.getBlockZ());
        double height = rocket.getY() - ground;
        if (!rocket.parachute() && height < PARACHUTE_HEIGHT) {
            rocket.setParachute(true);
            sound(world, SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA, 2.0f, 0.6f);
        }
        if (rocket.parachute()) {
            velY += (-0.2 - velY) * 0.08;
        } else {
            velY = Math.max(velY - 0.03, -3.0);
            if (tick % 20 == 0) {
                sound(world, SoundEvents.BLOCK_FIRE_AMBIENT, 2.0f, 0.5f);
            }
        }
        rocket.move(MovementType.SELF, new Vec3d(0, velY, 0));
        if (rocket.isOnGround() || rocket.isTouchingWater() || rocket.verticalCollision) {
            land(world);
        }
    }

    private void land(ServerWorld world) {
        rocket.setParachute(false);
        rocket.setPhase(FlightPhase.LANDED);
        rocket.setFlight(null);
        sound(world, SoundEvents.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(crew);
        if (player != null && player.getVehicle() == rocket) {
            player.stopRiding();
            // Космонавт выходит рядом и оборачивается к капсуле
            player.networkHandler.requestTeleport(rocket.getX() + 2.5, rocket.getY(), rocket.getZ(), 90f, 15f);
            player.fallDistance = 0;
            player.sendMessage(Text.translatable("message.orbitcore.flight.landed").formatted(Formatting.GREEN), true);
        }
    }

    private void emergencyLanding(ServerWorld world) {
        List<RocketDesign.Entry> capsule = rocket.design().parts().stream()
                .filter(e -> e.part().kind() == RocketPart.Kind.CAPSULE)
                .toList();
        rocket.setDesign(new RocketDesign(capsule));
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, rocket.getBlockPos());
        rocket.refreshPositionAndAngles(rocket.getX(), top.getY(), rocket.getZ(), 0, 0);
        land(world);
    }

    private void sound(ServerWorld world, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, rocket.getX(), rocket.getY(), rocket.getZ(), sound, SoundCategory.NEUTRAL, volume, pitch);
    }
}
