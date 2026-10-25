package com.orbitcore.entity;

import com.orbitcore.item.RocketItem;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModItems;
import com.orbitcore.rocket.RocketDesign;
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

import java.util.List;

/**
 * Ракета на стартовом столе. Стоит неподвижно, её можно заправить вёдрами
 * керосина и жидкого кислорода, осмотреть и разобрать обратно в предмет (Shift + пустая рука).
 * Полёт появится на этапе 0.5.
 */
public class RocketEntity extends Entity {
    private static final TrackedData<NbtCompound> PARTS = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<Integer> KEROSENE = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OXYGEN = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private RocketDesign design = new RocketDesign(List.of());

    public RocketEntity(EntityType<?> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(PARTS, new NbtCompound());
        dataTracker.startTracking(KEROSENE, 0);
        dataTracker.startTracking(OXYGEN, 0);
    }

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

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient && oxygen() > 0 && random.nextInt(3) == 0) {
            // Жидкий кислород кипит в баках — холодный пар стекает по бортам
            double angle = random.nextDouble() * Math.PI * 2;
            double y = getY() + 0.4 + random.nextDouble() * Math.max(0.5, design.height() * 0.7);
            getWorld().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(angle) * 0.45, y, getZ() + Math.sin(angle) * 0.45,
                    Math.cos(angle) * 0.02, -0.03, Math.sin(angle) * 0.02);
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        World world = getWorld();
        if (stack.isOf(ModFluids.KEROSENE_BUCKET) || stack.isOf(ModFluids.LIQUID_OXYGEN_BUCKET)) {
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
                ItemStack rocket = RocketItem.create(ModItems.ASSEMBLED_ROCKET, design, kerosene(), oxygen());
                if (!player.getInventory().insertStack(rocket)) {
                    dropStack(rocket);
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
        return !isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        NbtCompound tag = new NbtCompound();
        tag.put("Parts", nbt.getList("Parts", NbtElement.COMPOUND_TYPE));
        dataTracker.set(PARTS, tag);
        setFuel(nbt.getInt("Kerosene"), nbt.getInt("Oxygen"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("Parts", design.toNbt());
        nbt.putInt("Kerosene", kerosene());
        nbt.putInt("Oxygen", oxygen());
    }
}
