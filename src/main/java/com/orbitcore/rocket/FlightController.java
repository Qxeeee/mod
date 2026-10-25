package com.orbitcore.rocket;

import com.orbitcore.block.LaunchPadBlock;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.entity.RocketStageEntity;
import com.orbitcore.item.SpacesuitItem;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModGameRules;
import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.rocket.RocketDesign.Destination;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Полёт ракеты на сервере: отсчёт, подъём с отделением ступеней, орбита, перелёт
 * к Луне, управляемая посадка, взлёт обратно и вход в атмосферу Земли с парашютом.
 * Клиенту уходят только этап, такт и несколько чисел — по ним он сам ставит камеру,
 * субтитры и эффекты.
 */
public final class FlightController {
    public static final int COUNTDOWN_TICKS = 200;
    public static final int IGNITION_TICK = 140;
    /** Высота над столом, на которой начинается космос. */
    public static final double SPACE_ALTITUDE = 420;
    /** С Луны в космос — ниже: там нет атмосферы и сила тяжести слабее. */
    public static final double MOON_SPACE_ALTITUDE = 220;
    public static final int ORBIT_TICKS = 700;
    public static final int TRANSFER_TICKS = 360;
    public static final double REENTRY_ALTITUDE = 380;
    public static final double PARACHUTE_HEIGHT = 60;
    public static final double DESCENT_HEIGHT = 150;
    /** Скорость касания, с которой посадка ещё мягкая: 0,3 блока за такт ≈ 6 м/с. */
    public static final double SAFE_TOUCHDOWN = 0.3;
    /** Сколько дельта-V уходит на выход в космос и на перелёт к Луне. */
    public static final int ORBIT_DELTA_V = 3000;
    public static final int TRANSFER_DELTA_V = 500;
    /** Ускорение свободного падения Minecraft (блоков за такт²) — это 1 g. */
    private static final double G = 0.08;
    /** Перевод ускорения из блоков за такт² в м/с за такт (1 g = 9,81 м/с²). */
    private static final double DV_PER_TICK = 9.81 / G * 0.05;

    private RocketEntity rocket;
    private final UUID crew;
    private final Destination destination;
    private final List<Double> separations = new ArrayList<>();
    private double velX;
    private double velY;
    private int separated;
    private boolean maneuver;
    private int crewMissing;
    private double landingBudget;

    private FlightController(RocketEntity rocket, UUID crew, Destination destination) {
        this.rocket = rocket;
        this.crew = crew;
        this.destination = destination;
    }

    /** Цели, к которым можно стартовать из этого мира. */
    public static boolean isAvailable(Destination destination, World world) {
        boolean moon = world.getRegistryKey() == ModDimensions.MOON;
        return destination != Destination.MOON_RETURN && destination.fromMoon() == moon;
    }

    /** Почему ракета не может лететь туда сейчас, или null, если может. */
    @Nullable
    public static Text whyCannotLaunch(RocketEntity rocket, ServerPlayerEntity player, Destination destination) {
        World world = rocket.getWorld();
        RocketDesign design = rocket.design();
        boolean moon = world.getRegistryKey() == ModDimensions.MOON;
        if (rocket.phase() != FlightPhase.IDLE) {
            return Text.translatable("message.orbitcore.flight.busy");
        }
        if (!isAvailable(destination, world)) {
            return Text.translatable("message.orbitcore.flight.wrong_world");
        }
        if (!moon && !LaunchPadBlock.isComplete(world, rocket.getBlockPos().down())) {
            return Text.translatable("message.orbitcore.rocket.pad_incomplete");
        }
        if (!design.isValid()) {
            return Text.translatable("message.orbitcore.flight.bad_design");
        }
        if (!design.has(RocketPart.Kind.CAPSULE)) {
            return Text.translatable("rocket.orbitcore.problem.no_capsule");
        }
        if (design.liftoffTwr(rocket.fill(), ModDimensions.gravity(world)) <= 1.0) {
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

    /**
     * Самая высокая точка грунта под основанием ракеты. Ракета шириной в три блока
     * садится на верхний бугор под собой, а не на грунт под центром.
     */
    private static int groundUnder(World world, RocketEntity rocket) {
        Box box = rocket.getBoundingBox();
        int ground = world.getBottomY();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z));
            }
        }
        return ground;
    }

    /** Экипаж выходит из ракеты и встаёт рядом, на грунт, лицом к ней. */
    private static void stepOut(ServerPlayerEntity player, RocketEntity rocket, double distance, float pitch) {
        double x = rocket.getX() + RocketDesign.RADIUS + distance;
        double z = rocket.getZ();
        int ground = rocket.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        player.networkHandler.requestTeleport(x, Math.max(rocket.getY(), ground), z, 90f, pitch);
    }

    public static boolean engineerMode(World world) {
        return world.getGameRules().getBoolean(ModGameRules.ENGINEER_MODE);
    }

    /** Экипаж садится в капсулу, начинается отсчёт. */
    public static void launch(RocketEntity rocket, ServerPlayerEntity player, Destination destination) {
        FlightController flight = new FlightController(rocket, player.getUuid(), destination);
        double fill = rocket.fill();
        // Ступени, которые выгорят ещё до космоса, отделятся на подъёме
        int needed = switch (destination) {
            case SUBORBITAL -> Destination.SUBORBITAL.deltaV();
            case RETURN_TO_EARTH -> Destination.RETURN_TO_EARTH.deltaV();
            default -> ORBIT_DELTA_V;
        };
        List<Double> stageDv = rocket.design().stageDeltaV(fill);
        double sum = 0;
        for (int i = 0; i < stageDv.size() - 1; i++) {
            sum += stageDv.get(i);
            if (sum >= needed) {
                break;
            }
            flight.separations.add(sum / needed);
        }
        flight.landingBudget = rocket.design().deltaV(fill) - ORBIT_DELTA_V - TRANSFER_DELTA_V;
        if (rocket.getWorld().getRegistryKey() == World.OVERWORLD) {
            rocket.setHome(rocket.getX(), rocket.getZ());
        }
        rocket.setFlight(flight);
        rocket.setDestination(destination);
        rocket.setBaseY((float) rocket.getY());
        rocket.setSeparationTick(-1000);
        rocket.setThrottle(0);
        rocket.setPhase(FlightPhase.COUNTDOWN);
        player.startRiding(rocket, true);
        rocket.getWorld().playSound(null, rocket.getBlockPos(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.NEUTRAL, 1.0f, 0.6f);
        // В капсуле есть воздух, а снаружи на Луне — нет
        if (destination == Destination.MOON_LANDING && !SpacesuitItem.isWearingFullSuit(player) && !player.getAbilities().invulnerable) {
            player.sendMessage(Text.translatable("message.orbitcore.flight.no_spacesuit").formatted(Formatting.GOLD));
        }
    }

    /** Полёт, прерванный перезапуском сервера: капсула сразу садится там, где была. */
    public static void recover(RocketEntity rocket) {
        FlightController flight = new FlightController(rocket, new UUID(0, 0), Destination.SUBORBITAL);
        flight.crewMissing = 1000;
        rocket.setFlight(flight);
    }

    // ------------------------------------------------------------------ команды экипажа

    /** Пропустить текущую сцену: подъём сразу заканчивается в космосе, перелёт — у цели. */
    public void skip() {
        switch (rocket.phase()) {
            case COUNTDOWN, ASCENT -> {
                while (separated < separations.size()) {
                    separate(false);
                }
                rocket.setPosition(rocket.getX() + velX * 40, rocket.baseY() + spaceAltitude(), rocket.getZ());
                reachSpace();
            }
            case TRANSFER -> rocket.setFlightTick(TRANSFER_TICKS);
            default -> {
            }
        }
    }

    /** Манёвр на орбите: сход с орбиты или разгон к Луне. */
    public void requestManeuver() {
        maneuver = true;
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
                if (maneuver || tick >= ORBIT_TICKS) {
                    maneuver = false;
                    if (destination == Destination.MOON_LANDING) {
                        rocket.setPhase(FlightPhase.TRANSFER);
                        sound(world, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2.0f, 0.5f);
                    } else {
                        startReentry(world);
                    }
                }
            }
            case TRANSFER -> {
                if (tick >= TRANSFER_TICKS) {
                    arrive(world);
                }
            }
            case DESCENT -> descent(world);
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
        if (player.getVehicle() != rocket && player.getWorld() == world) {
            player.startRiding(rocket, true);
        }
        return true;
    }

    private boolean onMoon() {
        return rocket.getWorld().getRegistryKey() == ModDimensions.MOON;
    }

    private double spaceAltitude() {
        return onMoon() ? MOON_SPACE_ALTITUDE : SPACE_ALTITUDE;
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
        double accel = (0.005 + 0.004 * separated) * (onMoon() ? 1.6 : 1.0);
        velY = Math.min(velY + accel, 3.2);
        if (tick > 160) {
            // Разворот по тангажу: ракета уходит на восток, как настоящие
            velX = Math.min(velX + 0.0012, 0.6);
        }
        rocket.setPosition(rocket.getX() + velX, rocket.getY() + velY, rocket.getZ());
        rocket.setVerticalSpeed((float) velY);
        if (tick % 30 == 0 && tick < 400) {
            sound(world, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 0.4f);
        }
        double progress = (rocket.getY() - rocket.baseY()) / spaceAltitude();
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
            // Экипаж слышит отделение у себя в кабине, остальные — снаружи
            ServerWorld world = (ServerWorld) rocket.getWorld();
            world.playSound(world.getServer().getPlayerManager().getPlayer(crew), rocket.getX(), rocket.getY(), rocket.getZ(),
                    ModSounds.FLIGHT_SEPARATION, SoundCategory.NEUTRAL, 4.0f, 1.0f);
        }
        rocket.setDesign(upper);
        rocket.setPosition(rocket.getX(), rocket.getY() + drop, rocket.getZ());
        rocket.setSeparationTick(rocket.flightTick());
    }

    private void reachSpace() {
        ServerWorld world = (ServerWorld) rocket.getWorld();
        velX = 0;
        velY = 0;
        switch (destination) {
            case SUBORBITAL -> {
                reward(world, ScienceType.TELEMETRY, 4);
                startReentry(world);
            }
            case RETURN_TO_EARTH -> rocket.setPhase(FlightPhase.TRANSFER);
            default -> {
                reward(world, ScienceType.TELEMETRY, 10);
                rocket.setPhase(FlightPhase.SPACE);
            }
        }
    }

    // ------------------------------------------------------------------ перелёт между мирами

    /** Конец перелёта: ракета появляется над целью в другом мире. */
    private void arrive(ServerWorld world) {
        MinecraftServer server = world.getServer();
        if (destination == Destination.RETURN_TO_EARTH) {
            ServerWorld earth = server.getOverworld();
            double x = rocket.hasHome() ? rocket.homeX() : earth.getSpawnPos().getX() + 0.5;
            double z = rocket.hasHome() ? rocket.homeZ() : earth.getSpawnPos().getZ() + 0.5;
            RocketEntity capsule = moveTo(earth, x, z, REENTRY_ALTITUDE, true);
            if (capsule != null) {
                capsule.setBaseY((float) earth.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)));
                startReentry(earth);
            }
            return;
        }
        ServerWorld moon = server.getWorld(ModDimensions.MOON);
        if (moon == null) {
            startReentry(world);
            return;
        }
        RocketEntity lander = moveTo(moon, rocket.getX(), rocket.getZ(), DESCENT_HEIGHT, false);
        if (lander != null) {
            velY = -1.0;
            lander.setLandingDeltaV((float) Math.max(0, landingBudget));
            lander.setAutopilot(!engineerMode(moon));
            lander.setPhase(FlightPhase.DESCENT);
        }
    }

    /** Переносит ракету и экипаж в другой мир на высоту {@code height} над поверхностью. */
    @Nullable
    private RocketEntity moveTo(ServerWorld target, double x, double z, double height, boolean capsuleOnly) {
        ServerPlayerEntity player = target.getServer().getPlayerManager().getPlayer(crew);
        target.getChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        int ground = target.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        double y = ground + height;
        RocketEntity copy = ModEntities.ROCKET.create(target);
        if (copy == null) {
            return null;
        }
        RocketDesign design = capsuleOnly ? capsuleOf(rocket.design()) : rocket.design();
        copy.refreshPositionAndAngles(x, y, z, 0, 0);
        copy.setDesign(design);
        copy.setDestination(destination);
        copy.setBaseY(ground);
        if (rocket.hasHome()) {
            copy.setHome(rocket.homeX(), rocket.homeZ());
        }
        if (player != null) {
            player.stopRiding();
        }
        rocket.discard();
        rocket = copy;
        copy.setFlight(this);
        target.spawnEntity(copy);
        if (player != null) {
            FabricDimensions.teleport(player, target, new TeleportTarget(new Vec3d(x, y, z), Vec3d.ZERO, player.getYaw(), player.getPitch()));
            player.startRiding(copy, true);
        }
        return copy;
    }

    private static RocketDesign capsuleOf(RocketDesign design) {
        return new RocketDesign(design.parts().stream().filter(e -> e.part().kind() == RocketPart.Kind.CAPSULE).toList());
    }

    // ------------------------------------------------------------------ посадка на Луну

    private void descent(ServerWorld world) {
        double gravity = G * ModDimensions.gravity(world);
        RocketDesign design = rocket.design();
        double maxAccel = design.liftoffTwr(0.5) * G;
        int ground = groundUnder(world, rocket);
        double height = rocket.getY() - ground;

        float throttle = rocket.autopilot() ? autopilot(height, gravity, maxAccel) : rocket.throttle();
        if (rocket.landingDeltaV() <= 0) {
            throttle = 0;
        }
        rocket.setThrottle(throttle);
        double accel = throttle * maxAccel;
        rocket.setLandingDeltaV((float) Math.max(0, rocket.landingDeltaV() - accel * DV_PER_TICK));
        velY += accel - gravity;
        rocket.setVerticalSpeed((float) velY);
        if (throttle > 0.05f && rocket.flightTick() % 12 == 0) {
            sound(world, SoundEvents.BLOCK_FIRE_AMBIENT, 1.5f, 0.6f);
        }
        rocket.move(MovementType.SELF, new Vec3d(0, velY, 0));
        if (rocket.isOnGround() || rocket.verticalCollision) {
            touchdown(world, -velY);
        }
    }

    /** Автопилот: тормозит так, чтобы скорость снижения падала вместе с высотой. */
    private float autopilot(double height, double gravity, double maxAccel) {
        double target = -Math.max(0.08, Math.min(1.2, height * 0.022));
        double needed = target - velY + gravity;
        return (float) Math.max(0, Math.min(1, needed / Math.max(1e-6, maxAccel)));
    }

    private void touchdown(ServerWorld world, double impact) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(crew);
        boolean legs = rocket.design().has(RocketPart.Kind.LEGS);
        if (impact > SAFE_TOUCHDOWN || !legs) {
            crash(world, player, legs);
            return;
        }
        // Мягкая посадка: ракета стоит на опорах, остаток топлива — в баках
        RocketDesign design = rocket.design();
        double fill = design.fillFor(rocket.landingDeltaV());
        int fuel = (int) (design.propellantCapacity() * fill);
        rocket.setFuel(fuel, fuel);
        rocket.setThrottle(0);
        rocket.setVerticalSpeed(0);
        rocket.setPhase(FlightPhase.IDLE);
        rocket.setFlight(null);
        sound(world, SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
        MinecraftServer server = world.getServer();
        reward(world, ScienceType.TELEMETRY, 20);
        boolean first = SpaceProgram.get(server).markFirst(server, "moon_landing");
        if (player != null) {
            player.stopRiding();
            stepOut(player, rocket, 4, -10f);
            player.fallDistance = 0;
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("flight.orbitcore.moon_title")));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable(first
                    ? "flight.orbitcore.moon_subtitle_first" : "flight.orbitcore.moon_subtitle")));
            if (first) {
                server.getPlayerManager().broadcast(Text.translatable("message.orbitcore.flight.first_moon_landing",
                        player.getDisplayName()).formatted(Formatting.GOLD), false);
            }
        }
    }

    /** Жёсткая посадка: ракета разбивается, космонавта выбрасывает рядом. */
    private void crash(ServerWorld world, @Nullable ServerPlayerEntity player, boolean legs) {
        if (player != null) {
            player.stopRiding();
            stepOut(player, rocket, 5, 5f);
            player.fallDistance = 0;
            player.sendMessage(Text.translatable(legs ? "message.orbitcore.flight.crash" : "message.orbitcore.flight.crash_no_legs")
                    .formatted(Formatting.RED), false);
        }
        world.createExplosion(rocket, rocket.getX(), rocket.getY() + 0.5, rocket.getZ(), 2.5f, World.ExplosionSourceType.NONE);
        rocket.dropStack(new ItemStack(ModItems.STEEL_PLATE, 3));
        rocket.setFlight(null);
        rocket.discard();
    }

    // ------------------------------------------------------------------ возвращение на Землю

    private void reward(ServerWorld world, ScienceType type, int amount) {
        MinecraftServer server = world.getServer();
        SpaceProgram.get(server).addPoints(server, type, amount);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(crew);
        if (player != null) {
            player.sendMessage(Text.translatable("message.orbitcore.science_gained", amount,
                    Text.translatable(type.translationKey())).formatted(Formatting.AQUA), true);
        }
    }

    /** Капсула отделяется от остатков ракеты и входит в атмосферу недалеко от старта. */
    private void startReentry(ServerWorld world) {
        rocket.setDesign(capsuleOf(rocket.design()));
        double landX = Math.floor(rocket.getX() - velX * 30) + 0.5 + (world.random.nextDouble() - 0.5) * 40;
        double landZ = Math.floor(rocket.getZ()) + 0.5 + (world.random.nextDouble() - 0.5) * 40;
        rocket.refreshPositionAndAngles(landX, rocket.baseY() + REENTRY_ALTITUDE, landZ, 0, 0);
        velX = 0;
        velY = -1.0;
        rocket.setParachute(false);
        rocket.setPhase(FlightPhase.REENTRY);
    }

    private void reentry(ServerWorld world, int tick) {
        int ground = groundUnder(world, rocket);
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
        rocket.setVerticalSpeed((float) velY);
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
            stepOut(player, rocket, 4, -5f);
            player.fallDistance = 0;
            player.sendMessage(Text.translatable("message.orbitcore.flight.landed").formatted(Formatting.GREEN), true);
        }
    }

    private void emergencyLanding(ServerWorld world) {
        rocket.setDesign(capsuleOf(rocket.design()));
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, rocket.getBlockPos());
        rocket.refreshPositionAndAngles(rocket.getX(), top.getY(), rocket.getZ(), 0, 0);
        land(world);
    }

    private void sound(ServerWorld world, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, rocket.getX(), rocket.getY(), rocket.getZ(), sound, SoundCategory.NEUTRAL, volume, pitch);
    }
}
