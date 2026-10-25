package com.orbitcore.entity;

import com.orbitcore.item.RocketItem;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModItems;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ракета. На столе её заправляют вёдрами и осматривают; из экрана ракеты экипаж
 * садится в капсулу и стартует. Логика полёта — в {@link FlightController} (только сервер),
 * а клиент по синхронизированным полям строит кино: огонь, дым, камеру и субтитры.
 */
public class RocketEntity extends Entity {
    private static final TrackedData<NbtCompound> PARTS = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<Integer> KEROSENE = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OXYGEN = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FLIGHT_TICK = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> DESTINATION = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> SEPARATION_TICK = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> BASE_Y = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> THROTTLE = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> PARACHUTE = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private RocketDesign design = new RocketDesign(List.of());
    @Nullable
    private FlightController flight;

    public RocketEntity(EntityType<?> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(PARTS, new NbtCompound());
        dataTracker.startTracking(KEROSENE, 0);
        dataTracker.startTracking(OXYGEN, 0);
        dataTracker.startTracking(PHASE, FlightPhase.IDLE.ordinal());
        dataTracker.startTracking(FLIGHT_TICK, 0);
        dataTracker.startTracking(DESTINATION, 0);
        dataTracker.startTracking(SEPARATION_TICK, -1000);
        dataTracker.startTracking(BASE_Y, 0f);
        dataTracker.startTracking(THROTTLE, 0f);
        dataTracker.startTracking(PARACHUTE, false);
    }

    // ------------------------------------------------------------------ конструкция и топливо

    public RocketDesign design() {
        return design;
    }

    public void setDesign(RocketDesign design) {
        NbtCompound tag = new NbtCompound();
        tag.put("Parts", design.toNbt());
        dataTracker.set(PARTS, tag);
    }

    public int kerosene() {
        return dataTracker.get(KEROSENE);
    }

    public int oxygen() {
        return dataTracker.get(OXYGEN);
    }

    public void setFuel(int kerosene, int oxygen) {
        int capacity = design.propellantCapacity();
        dataTracker.set(KEROSENE, Math.max(0, Math.min(capacity, kerosene)));
        dataTracker.set(OXYGEN, Math.max(0, Math.min(capacity, oxygen)));
    }

    /** Доля заправки 0–1: ракета летит на том, чего меньше — керосина или кислорода. */
    public double fill() {
        int capacity = design.propellantCapacity();
        return capacity == 0 ? 0 : Math.min(kerosene(), oxygen()) / (double) capacity;
    }

    // ------------------------------------------------------------------ состояние полёта (синхронизируется)

    public FlightPhase phase() {
        return FlightPhase.byId(dataTracker.get(PHASE));
    }

    public void setPhase(FlightPhase phase) {
        dataTracker.set(PHASE, phase.ordinal());
        dataTracker.set(FLIGHT_TICK, 0);
    }

    public int flightTick() {
        return dataTracker.get(FLIGHT_TICK);
    }

    public void setFlightTick(int tick) {
        dataTracker.set(FLIGHT_TICK, tick);
    }

    public RocketDesign.Destination destination() {
        return RocketDesign.Destination.values()[Math.min(dataTracker.get(DESTINATION), RocketDesign.Destination.values().length - 1)];
    }

    public void setDestination(RocketDesign.Destination destination) {
        dataTracker.set(DESTINATION, destination.ordinal());
    }

    /** Такт подъёма, на котором отделилась последняя ступень (для кадра с отделением). */
    public int separationTick() {
        return dataTracker.get(SEPARATION_TICK);
    }

    public void setSeparationTick(int tick) {
        dataTracker.set(SEPARATION_TICK, tick);
    }

    /** Высота стола, от которой считается высота полёта. */
    public float baseY() {
        return dataTracker.get(BASE_Y);
    }

    public void setBaseY(float y) {
        dataTracker.set(BASE_Y, y);
    }

    public float throttle() {
        return dataTracker.get(THROTTLE);
    }

    public void setThrottle(float throttle) {
        dataTracker.set(THROTTLE, Math.max(0f, Math.min(1f, throttle)));
    }

    public boolean parachute() {
        return dataTracker.get(PARACHUTE);
    }

    public void setParachute(boolean open) {
        dataTracker.set(PARACHUTE, open);
    }

    @Nullable
    public FlightController flight() {
        return flight;
    }

    public void setFlight(@Nullable FlightController flight) {
        this.flight = flight;
    }

    // ------------------------------------------------------------------ размеры и посадка экипажа

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (PARTS.equals(data)) {
            design = RocketDesign.fromNbt(dataTracker.get(PARTS).getList("Parts", NbtElement.COMPOUND_TYPE));
            calculateDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return EntityDimensions.changing(1.0f, design.height());
    }

    /** Космонавт сидит в капсуле: над всеми модулями, что стоят под ней. */
    @Override
    public double getMountedHeightOffset() {
        int px = 0;
        for (RocketDesign.Entry entry : design.parts()) {
            if (entry.part().kind() == RocketPart.Kind.CAPSULE) {
                break;
            }
            px += entry.part().heightPx();
        }
        return px / 16.0 + 0.15;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().isEmpty();
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 512 * 512;
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            spawnEffects();
        } else if (flight != null) {
            flight.tick();
        }
    }

    private void spawnEffects() {
        World world = getWorld();
        FlightPhase phase = phase();
        double x = getX();
        double y = getY();
        double z = getZ();
        boolean engines = phase == FlightPhase.ASCENT
                || phase == FlightPhase.COUNTDOWN && flightTick() >= FlightController.IGNITION_TICK
                || phase == FlightPhase.DESCENT && throttle() > 0.05f;
        if (engines) {
            int count = phase == FlightPhase.DESCENT ? 2 + (int) (throttle() * 4) : 8;
            for (int i = 0; i < count; i++) {
                double ox = (random.nextDouble() - 0.5) * 0.6;
                double oz = (random.nextDouble() - 0.5) * 0.6;
                world.addImportantParticle(ParticleTypes.FLAME, x + ox, y - 0.1, z + oz, ox * 0.2, -0.6 - random.nextDouble() * 0.5, oz * 0.2);
            }
            world.addImportantParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y - 0.6, z,
                    (random.nextDouble() - 0.5) * 0.05, -0.05, (random.nextDouble() - 0.5) * 0.05);
            // Облако у стола в первые секунды
            if (y - baseY() < 12 && phase != FlightPhase.DESCENT) {
                for (int i = 0; i < 6; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double speed = 0.25 + random.nextDouble() * 0.35;
                    world.addParticle(ParticleTypes.CLOUD, x, baseY() + 0.3, z,
                            Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed);
                }
                world.addParticle(ParticleTypes.LARGE_SMOKE, x + (random.nextDouble() - 0.5) * 3, baseY() + 0.5,
                        z + (random.nextDouble() - 0.5) * 3, 0, 0.08, 0);
            }
        } else if (phase == FlightPhase.REENTRY && !parachute()) {
            // Плазма вокруг теплозащиты
            for (int i = 0; i < 6; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                world.addImportantParticle(ParticleTypes.FLAME, x + Math.cos(angle) * 0.5, y + 0.1, z + Math.sin(angle) * 0.5,
                        Math.cos(angle) * 0.1, 0.4, Math.sin(angle) * 0.1);
            }
            world.addImportantParticle(ParticleTypes.LARGE_SMOKE, x, y + 1.2, z, 0, 0.3, 0);
        } else if (phase == FlightPhase.IDLE && oxygen() > 0 && random.nextInt(3) == 0) {
            // Жидкий кислород кипит в баках — холодный пар стекает по бортам
            double angle = random.nextDouble() * Math.PI * 2;
            double vy = y + 0.4 + random.nextDouble() * Math.max(0.5, design.height() * 0.7);
            world.addParticle(ParticleTypes.CLOUD, x + Math.cos(angle) * 0.45, vy, z + Math.sin(angle) * 0.45,
                    Math.cos(angle) * 0.02, -0.03, Math.sin(angle) * 0.02);
        }
    }

    // ------------------------------------------------------------------ взаимодействие

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        World world = getWorld();
        FlightPhase phase = phase();
        if (phase.inFlight()) {
            return ActionResult.PASS;
        }
        if (phase == FlightPhase.IDLE && (stack.isOf(ModFluids.KEROSENE_BUCKET) || stack.isOf(ModFluids.LIQUID_OXYGEN_BUCKET))) {
            boolean kerosene = stack.isOf(ModFluids.KEROSENE_BUCKET);
            int current = kerosene ? kerosene() : oxygen();
            if (current + 1000 > design.propellantCapacity()) {
                if (!world.isClient) {
                    player.sendMessage(Text.translatable("message.orbitcore.rocket.tanks_full").formatted(Formatting.GRAY), true);
                }
                return ActionResult.FAIL;
            }
            if (!world.isClient) {
                setFuel(kerosene ? current + 1000 : kerosene(), kerosene ? oxygen() : current + 1000);
                if (!player.getAbilities().creativeMode) {
                    player.setStackInHand(hand, new ItemStack(Items.BUCKET));
                }
                world.playSound(null, getBlockPos(), SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.NEUTRAL, 1.0f, kerosene ? 0.8f : 1.3f);
            }
            return ActionResult.success(world.isClient);
        }
        if (player.isSneaking() && stack.isEmpty()) {
            if (!world.isClient) {
                // Ракету на столе снимают целиком, капсулу после посадки — как модуль
                ItemStack item = phase == FlightPhase.LANDED
                        ? new ItemStack(ModItems.CAPSULE_KAPLYA)
                        : RocketItem.create(ModItems.ASSEMBLED_ROCKET, design, kerosene(), oxygen());
                if (!player.getInventory().insertStack(item)) {
                    dropStack(item);
                }
                world.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.5f, 0.7f);
                discard();
            }
            return ActionResult.success(world.isClient);
        }
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            OrbitNetworking.openRocket(serverPlayer, this);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canHit() {
        return !isRemoved() && !phase().inFlight();
    }

    @Override
    public boolean isCollidable() {
        return !phase().inFlight();
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        NbtCompound tag = new NbtCompound();
        tag.put("Parts", nbt.getList("Parts", NbtElement.COMPOUND_TYPE));
        dataTracker.set(PARTS, tag);
        setFuel(nbt.getInt("Kerosene"), nbt.getInt("Oxygen"));
        FlightPhase phase = FlightPhase.byId(nbt.getInt("Phase"));
        setBaseY(nbt.getFloat("BaseY"));
        // Полёт, прерванный перезапуском сервера, заканчивается мягкой посадкой
        setPhase(phase.inFlight() ? FlightPhase.IDLE : phase);
        if (phase.inFlight()) {
            FlightController.recover(this);
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("Parts", design.toNbt());
        nbt.putInt("Kerosene", kerosene());
        nbt.putInt("Oxygen", oxygen());
        nbt.putInt("Phase", phase().ordinal());
        nbt.putFloat("BaseY", baseY());
    }
}
