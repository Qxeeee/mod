package com.orbitcore.entity;

import com.orbitcore.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Наблюдатель: фигура в пыльном скафандре «Зари» с чёрным визором. Стоит поодаль
 * и смотрит на игрока, не двигаясь. Стоит посмотреть на неё прямо или подойти —
 * её больше нет. Ничего не делает. И не сохраняется в мире.
 */
public class WatcherEntity extends MobEntity {
    /** Сколько тиков он стоит, если на него так и не посмотрели. */
    private static final int LIFETIME = 20 * 30;

    @Nullable
    private UUID target;

    public WatcherEntity(EntityType<? extends WatcherEntity> type, World world) {
        super(type, world);
        setAiDisabled(true);
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes();
    }

    public void watch(ServerPlayerEntity player) {
        target = player.getUuid();
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }
        ServerPlayerEntity player = target == null ? null : (ServerPlayerEntity) getWorld().getPlayerByUuid(target);
        if (player == null || age > LIFETIME) {
            vanish();
            return;
        }
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        setYaw(yaw);
        setHeadYaw(yaw);
        setBodyYaw(yaw);
        if (age > 30 && (squaredDistanceTo(player) < 10 * 10 || seenBy(player))) {
            vanish();
        }
    }

    /** Игрок смотрит прямо на него, и между ними ничего нет. */
    public boolean seenBy(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d to = getEyePos().subtract(player.getEyePos()).normalize();
        return look.dotProduct(to) > 0.985 && player.canSee(this);
    }

    public void vanish() {
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.0, getZ(), 24, 0.25, 0.7, 0.25, 0.01);
            world.playSound(null, getX(), getY(), getZ(), ModSounds.ECHO_STATIC, SoundCategory.HOSTILE, 0.8f, 0.6f);
        }
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && !isRemoved()) {
            vanish();
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }
}
