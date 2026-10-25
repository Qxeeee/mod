package com.orbitcore.entity;

import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.world.CraterFeature;
import com.orbitcore.world.ModDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Метеорит: летит по прямой с огромной скоростью и, ударившись о грунт,
 * выбивает кратер с никелево-железным обломком на дне.
 * В атмосфере он горит и дымит, в вакууме — только искрит.
 */
public class MeteorEntity extends Entity {
    /** Радиус кратера в блоках. */
    private int size = 4;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, World world) {
        super(type, world);
        noClip = true;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        World world = getWorld();
        Vec3d from = getPos();
        Vec3d to = from.add(getVelocity());
        if (world.isClient) {
            trail(world, from);
            setPosition(to);
            return;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            impact((ServerWorld) world, hit.getBlockPos());
            return;
        }
        setPosition(to);
        // Улетел за мир или в незагруженные чанки — просто исчезает
        if (age > 400 || getY() < world.getBottomY()) {
            discard();
        }
    }

    private void trail(World world, Vec3d pos) {
        boolean air = ModDimensions.hasAir(world);
        for (int i = 0; i < 10; i++) {
            Vec3d p = pos.subtract(getVelocity().multiply(i / 10.0));
            double jitter = 0.5;
            double x = p.x + (random.nextDouble() - 0.5) * jitter;
            double y = p.y + (random.nextDouble() - 0.5) * jitter;
            double z = p.z + (random.nextDouble() - 0.5) * jitter;
            if (air) {
                world.addImportantParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
                world.addImportantParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.02, 0);
            } else {
                // Без воздуха он не горит, но раскалён ударом о солнечный ветер — фантастика, зато видно
                world.addImportantParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
                if (i % 3 == 0) {
                    world.addImportantParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
                }
            }
        }
    }

    /** Удар: кратер, обломок метеорита на дне, ударная волна. */
    public void impact(ServerWorld world, BlockPos at) {
        BlockPos bottom = CraterFeature.blast(world, at, size, world.random);
        world.setBlockState(bottom, ModBlocks.METEORITE.getDefaultState());
        if (size >= 5) {
            world.setBlockState(bottom.up(), ModBlocks.METEORITE.getDefaultState());
        }
        Vec3d center = Vec3d.ofCenter(bottom);
        world.createExplosion(this, center.x, center.y + 1, center.z, size * 0.6f, World.ExplosionSourceType.NONE);
        world.playSound(null, bottom, ModSounds.METEOR_IMPACT, SoundCategory.AMBIENT, 4.0f, 0.7f + random.nextFloat() * 0.2f);
        world.spawnParticles(ParticleTypes.CLOUD, center.x, center.y + 1, center.z, 60, size * 0.6, 1.0, size * 0.6, 0.15);
        world.spawnParticles(ParticleTypes.LAVA, center.x, center.y + 1, center.z, 12, 1.0, 0.5, 1.0, 0.1);
        discard();
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 256 * 256;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        size = Math.max(2, nbt.getInt("Size"));
        setVelocity(nbt.getDouble("VX"), nbt.getDouble("VY"), nbt.getDouble("VZ"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Size", size);
        Vec3d v = getVelocity();
        nbt.putDouble("VX", v.x);
        nbt.putDouble("VY", v.y);
        nbt.putDouble("VZ", v.z);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
