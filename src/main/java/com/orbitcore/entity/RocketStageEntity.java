package com.orbitcore.entity;

import com.orbitcore.rocket.RocketDesign;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;

import java.util.List;

/**
 * Отработавшая ступень: кувыркаясь, падает вниз, дымит и разбивается о землю
 * (взрыв без разрушения блоков, но стоять под ней не стоит).
 */
public class RocketStageEntity extends Entity {
    private static final TrackedData<NbtCompound> PARTS = DataTracker.registerData(RocketStageEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final int MAX_AGE = 1200;

    private RocketDesign design = new RocketDesign(List.of());

    public RocketStageEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(PARTS, new NbtCompound());
    }

    public RocketDesign design() {
        return design;
    }

    public void setDesign(RocketDesign design) {
        NbtCompound tag = new NbtCompound();
        tag.put("Parts", design.toNbt());
        dataTracker.set(PARTS, tag);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (PARTS.equals(data)) {
            design = RocketDesign.fromNbt(dataTracker.get(PARTS).getList("Parts", NbtElement.COMPOUND_TYPE));
        }
    }

    /** Угол кувыркания для рендера, градусы. */
    public float tumble(float tickDelta) {
        return (age + tickDelta) * 2.2f;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 512 * 512;
    }

    @Override
    public void tick() {
        super.tick();
        World world = getWorld();
        if (world.isClient) {
            world.addImportantParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 0, 0.02, 0);
            return;
        }
        setVelocity(getVelocity().multiply(0.99).add(0, -0.04, 0));
        move(MovementType.SELF, getVelocity());
        if (isOnGround() || isTouchingWater() || horizontalCollision || verticalCollision || age > MAX_AGE || getY() < world.getBottomY()) {
            if (age <= MAX_AGE && getY() >= world.getBottomY()) {
                world.createExplosion(this, getX(), getY(), getZ(), 2.0f, World.ExplosionSourceType.NONE);
            }
            discard();
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        NbtCompound tag = new NbtCompound();
        tag.put("Parts", nbt.getList("Parts", NbtElement.COMPOUND_TYPE));
        dataTracker.set(PARTS, tag);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("Parts", design.toNbt());
    }
}
