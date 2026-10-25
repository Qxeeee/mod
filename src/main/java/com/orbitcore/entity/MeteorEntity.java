package com.orbitcore.entity;

import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Box;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.block.BlockState;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModBlocks;
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

    /**
     * Удар: верхний слой грунта разлетается медленными дугами, на дне кратера —
     * раскалённый обломок. Воздуха нет, поэтому взрыва не слышно: игроки рядом
     * чувствуют толчок через грунт, видят вспышку, и у них звенит в ушах.
     */
    public void impact(ServerWorld world, BlockPos at) {
        ejecta(world, at);
        BlockPos bottom = CraterFeature.blast(world, at, size, world.random);
        world.setBlockState(bottom, ModBlocks.HOT_METEORITE.getDefaultState());
        if (size >= 5) {
            world.setBlockState(bottom.up(), ModBlocks.HOT_METEORITE.getDefaultState());
        }
        Vec3d center = Vec3d.ofCenter(bottom);
        shockwave(world, center);
        world.spawnParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 2, 0, 0, 0, 0);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ModBlocks.REGOLITH.getDefaultState()),
                center.x, center.y + 1, center.z, 160, size * 0.5, 0.4, size * 0.5, 0.8);
        world.spawnParticles(ParticleTypes.LAVA, center.x, center.y + 1, center.z, 16, 1.0, 0.5, 1.0, 0.1);
        for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(center) < 220 * 220)) {
            OrbitNetworking.sendMeteorImpact(player, center, size);
        }
        discard();
    }

    /** Выброс: глыбы грунта из будущего кратера летят наружу и вверх, при низкой тяжести — долго. */
    private void ejecta(ServerWorld world, BlockPos at) {
        int count = 6 + size * 2;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * size * 0.8;
            BlockPos column = BlockPos.ofFloored(at.getX() + Math.cos(angle) * r, at.getY(), at.getZ() + Math.sin(angle) * r);
            BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, column).down();
            BlockState state = world.getBlockState(top);
            if (state.isAir() || state.hasBlockEntity()) {
                continue;
            }
            FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, top, state);
            block.dropItem = false;
            block.setDestroyedOnLanding();
            double out = 0.15 + random.nextDouble() * 0.25;
            block.setVelocity(Math.cos(angle) * out, 0.25 + random.nextDouble() * 0.25, Math.sin(angle) * out);
            block.velocityModified = true;
        }
    }

    /** Ударная волна по грунту: бьёт и отбрасывает, но блоки не ломает. */
    private void shockwave(ServerWorld world, Vec3d center) {
        double radius = size * 1.8;
        for (Entity entity : world.getOtherEntities(this, new Box(center, center).expand(radius))) {
            double distance = entity.getPos().distanceTo(center);
            if (distance > radius || !(entity instanceof LivingEntity)) {
                continue;
            }
            float strength = (float) (1 - distance / radius);
            entity.damage(world.getDamageSources().explosion(this, null), size * 2.5f * strength);
            Vec3d push = entity.getPos().subtract(center).normalize().multiply(strength * 1.2);
            entity.addVelocity(push.x, 0.3 * strength, push.z);
            entity.velocityModified = true;
        }
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
