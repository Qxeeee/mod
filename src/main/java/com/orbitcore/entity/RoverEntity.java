package com.orbitcore.entity;

import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.world.ModDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Луноход: два места, электромотор, батарея на корме и солнечная панель над ней.
 * Водитель рулит обычными клавишами движения (W/S — газ и задний ход, A/D — поворот).
 * Батарея садится в пути и заряжается, пока луноход стоит на солнце.
 */
public class RoverEntity extends Entity {
    private static final TrackedData<Float> BATTERY = DataTracker.registerData(RoverEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Полный ход, блоков за тик (около 6 м/с). */
    public static final float MAX_SPEED = 0.3f;
    /** Сколько процентов батареи уходит на блок пути: полного заряда хватает примерно на 1200 блоков. */
    private static final double DRAIN_PER_BLOCK = 100.0 / 1200;
    /** Зарядка на солнце, процентов за тик: с нуля до полного — около восьми минут. */
    private static final float SOLAR_CHARGE = 0.01f;
    private static final float WHEEL_RADIUS = 7 / 16f;

    private float speed;
    private float yawDelta;
    private float wheelSpin;
    private float prevWheelSpin;
    private boolean measured;
    private double lastX;
    private double lastZ;
    private int lerpTicks;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYaw;

    public RoverEntity(EntityType<? extends RoverEntity> type, World world) {
        super(type, world);
        setStepHeight(1.0f);
        intersectionChecked = true;
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(BATTERY, 100.0f);
    }

    /** Заряд батареи, 0–100 %. */
    public float battery() {
        return dataTracker.get(BATTERY);
    }

    public void setBattery(float battery) {
        dataTracker.set(BATTERY, MathHelper.clamp(battery, 0, 100));
    }

    public float speed() {
        return speed;
    }

    /** Угол поворота колёс для рендера, радианы. */
    public float wheelSpin(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevWheelSpin, wheelSpin);
    }

    // ------------------------------------------------------------------ движение

    @Override
    public void tick() {
        super.tick();
        if (isLogicalSideForUpdatingMovement()) {
            lerpTicks = 0;
            updateTrackedPosition(getX(), getY(), getZ());
            LivingEntity driver = getControllingPassenger();
            drive(driver != null ? driver.forwardSpeed : 0, driver != null ? driver.sidewaysSpeed : 0);
        } else {
            interpolate();
        }
        if (!measured) {
            measured = true;
            // Первый тик: мерить путь не от чего, иначе «проезд» от нуля координат посадит батарею
            lastX = getX();
            lastZ = getZ();
        }
        Vec3d forward = Vec3d.fromPolar(0, getYaw());
        double dx = getX() - lastX;
        double dz = getZ() - lastZ;
        double moved = Math.sqrt(dx * dx + dz * dz);
        double signed = dx * forward.x + dz * forward.z;
        lastX = getX();
        lastZ = getZ();
        prevWheelSpin = wheelSpin;
        wheelSpin += (float) (signed / WHEEL_RADIUS);
        if (!getWorld().isClient) {
            serverTick(moved);
        }
    }

    /** Газ (−1…1) и руль (−1…1, плюс — влево). Без заряда мотор не тянет. */
    public void drive(float forward, float turn) {
        float target = battery() > 0 ? MathHelper.clamp(forward, -0.5f, 1.0f) * MAX_SPEED : 0;
        speed += (target - speed) * 0.08f;
        yawDelta = -turn * (1.5f + 3.0f * Math.abs(speed) / MAX_SPEED);
        setYaw(getYaw() + yawDelta);
        Vec3d facing = Vec3d.fromPolar(0, getYaw());
        double vy = isOnGround() ? -0.04 : getVelocity().y - 0.08 * ModDimensions.gravity(getWorld());
        setVelocity(facing.x * speed, Math.max(vy, -2.0), facing.z * speed);
        move(MovementType.SELF, getVelocity());
    }

    private void serverTick(double moved) {
        float battery = battery();
        if (moved > 0.01 && battery > 0) {
            battery = (float) Math.max(0, battery - moved * DRAIN_PER_BLOCK);
        }
        if (sunlit()) {
            battery = Math.min(100, battery + SOLAR_CHARGE);
        }
        setBattery(battery);
        if (moved > 0.02 && age % 40 == 0) {
            getWorld().playSound(null, getX(), getY(), getZ(), ModSounds.ROVER_MOTOR, SoundCategory.NEUTRAL, 0.6f,
                    0.8f + (float) moved * 1.5f);
        }
        if (age % 10 == 0 && getControllingPassenger() instanceof PlayerEntity driver) {
            Text status = battery <= 0
                    ? Text.translatable("message.orbitcore.rover.dead").formatted(Formatting.RED)
                    : Text.translatable("message.orbitcore.rover.status", Math.round(battery), String.format("%.1f", moved * 20))
                    .formatted(battery < 15 ? Formatting.GOLD : Formatting.GRAY);
            driver.sendMessage(status, true);
        }
    }

    /** Стоит под открытым небом, и на Луне сейчас день. */
    public boolean sunlit() {
        World world = getWorld();
        return MathHelper.cos(world.getSkyAngle(1.0f) * MathHelper.TAU) > 0 && world.isSkyVisible(getBlockPos().up(2));
    }

    private void interpolate() {
        if (lerpTicks <= 0) {
            return;
        }
        double x = getX() + (lerpX - getX()) / lerpTicks;
        double y = getY() + (lerpY - getY()) / lerpTicks;
        double z = getZ() + (lerpZ - getZ()) / lerpTicks;
        setYaw(getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpTicks);
        lerpTicks--;
        setPosition(x, y, z);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpTicks = 10;
    }

    // ------------------------------------------------------------------ пассажиры

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof PlayerEntity player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < 2;
    }

    @Override
    public double getMountedHeightOffset() {
        return 0.55;
    }

    /** Водитель садится слева, пассажир справа; оба поворачиваются вместе с луноходом. */
    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        int seat = getPassengerList().indexOf(passenger);
        Vec3d left = Vec3d.fromPolar(0, getYaw() - 90);
        double side = seat == 0 ? 0.44 : -0.44;
        double y = getY() + getMountedHeightOffset() + passenger.getHeightOffset();
        positionUpdater.accept(passenger, getX() + left.x * side, y, getZ() + left.z * side);
        passenger.setYaw(passenger.getYaw() + yawDelta);
        passenger.setHeadYaw(passenger.getHeadYaw() + yawDelta);
        if (passenger instanceof LivingEntity living) {
            living.setBodyYaw(getYaw());
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        World world = getWorld();
        if (player.isSneaking() && player.getStackInHand(hand).isEmpty() && !hasPassengers()) {
            if (!world.isClient) {
                // Луноход забирают целиком, вместе с зарядом
                ItemStack item = new ItemStack(ModItems.ROVER);
                item.getOrCreateNbt().putFloat("Battery", battery());
                if (!player.getInventory().insertStack(item)) {
                    dropStack(item);
                }
                world.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.5f, 1.2f);
                discard();
            }
            return ActionResult.success(world.isClient);
        }
        if (canAddPassenger(player) && !player.isSneaking()) {
            if (!world.isClient) {
                player.startRiding(this);
            }
            return ActionResult.success(world.isClient);
        }
        return ActionResult.PASS;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        setBattery(nbt.contains("Battery") ? nbt.getFloat("Battery") : 100);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("Battery", battery());
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
