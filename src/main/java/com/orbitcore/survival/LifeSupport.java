package com.orbitcore.survival;

import com.orbitcore.OrbitCore;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.item.SpacesuitItem;
import com.orbitcore.registry.ModComponents;
import com.orbitcore.registry.ModDamageTypes;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Жизнеобеспечение: дыхание, кислород скафандра, радиация и температура.
 * Считается на сервере для каждого игрока; итог уходит клиенту для HUD.
 */
public final class LifeSupport {
    /** Урон от удушья, когда кончился запас воздуха в лёгких. */
    public static final int SUFFOCATION_DAMAGE = 2;
    /** Пороги дозы, мЗв: голод, слабость, лучевая болезнь. */
    public static final float DOSE_HUNGER = 300;
    public static final float DOSE_WEAKNESS = 600;
    public static final float DOSE_SICKNESS = 900;
    public static final float DOSE_MAX = 1000;
    /** Безопасный диапазон без скафандра, °C. */
    public static final int BARE_MIN_TEMPERATURE = -10;
    public static final int BARE_MAX_TEMPERATURE = 45;
    /** Предупреждение о кислороде, секунд запаса. */
    public static final int LOW_OXYGEN = 60;

    private static final RegistryKey<Biome> ICE_CRATER = RegistryKey.of(RegistryKeys.BIOME, OrbitCore.id("moon_ice_crater"));

    private LifeSupport() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                tick(world, player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SealedAreas.clear());
    }

    public static LifeSupportComponent get(PlayerEntity player) {
        return ModComponents.LIFE_SUPPORT.get(player);
    }

    // ------------------------------------------------------------------ окружение

    /** Можно ли дышать в этой точке без скафандра. */
    public static boolean canBreathe(World world, BlockPos pos) {
        return ModDimensions.hasAir(world) || SealedAreas.isSealed(world, pos);
    }

    /** Под давлением ли игрок: в капсуле или в загерметизированном отсеке. */
    public static boolean pressurized(PlayerEntity player) {
        return player.getVehicle() instanceof RocketEntity
                || SealedAreas.isSealed(player.getWorld(), BlockPos.ofFloored(player.getEyePos()))
                || SealedAreas.isSealed(player.getWorld(), player.getBlockPos());
    }

    /** Высота Солнца: 1 в полдень, 0 на рассвете и закате, −1 в полночь. */
    public static float sunHeight(World world) {
        return MathHelper.cos(world.getSkyAngle(1.0f) * MathHelper.TAU);
    }

    /** Температура снаружи, °C. */
    public static int temperature(World world, BlockPos pos, boolean pressurized) {
        if (pressurized) {
            return 21;
        }
        if (ModDimensions.hasAir(world)) {
            return Math.round(world.getBiome(pos).value().getTemperature() * 25 - 1);
        }
        // Луна: в кратерах вечной тени Солнца не бывает никогда
        if (world.getBiome(pos).matchesKey(ICE_CRATER)) {
            return -200;
        }
        // В тени и под грунтом — ровный холод: реголит хорошо держит тепло
        if (!world.isSkyVisible(pos)) {
            return -20;
        }
        float sun = sunHeight(world);
        return Math.round(sun > 0 ? -20 + 140 * sun : -20 + 150 * sun);
    }

    /** Радиационный фон, мЗв/с: на Луне без магнитного поля и атмосферы. */
    public static float radiationRate(World world, BlockPos pos, boolean suit, boolean inCapsule) {
        if (ModDimensions.hasAir(world) || inCapsule || !world.isSkyVisible(pos)) {
            return 0;
        }
        // Днём — солнечный ветер, ночью — только космические лучи
        float rate = sunHeight(world) > 0 ? 1.0f : 0.2f;
        return suit ? rate / 2 : rate;
    }

    // ------------------------------------------------------------------ тик игрока

    static void tick(ServerWorld world, ServerPlayerEntity player) {
        LifeSupportComponent data = get(player);
        long time = world.getTime();
        boolean changed = false;
        boolean suit = SpacesuitItem.isWearingFullSuit(player);
        if (time % 10 == 0) {
            changed = updateEnvironment(world, player, data, suit);
        }
        // Творческий режим и наблюдатель неуязвимы — им не нужно ни дышать, ни греться
        boolean exempt = player.isSpectator() || player.getAbilities().invulnerable;

        // Дыхание
        boolean suffocating = false;
        if (!data.breathable && !exempt) {
            if (!suit || Oxygen.total(player) <= 0) {
                suffocating = true;
            } else if (time % 20 == 0) {
                suffocating = !Oxygen.consume(player, 1);
            }
            if (suit && time % 40 == 0) {
                int left = Oxygen.total(player);
                if (left < LOW_OXYGEN) {
                    player.playSound(ModSounds.SUIT_ALARM, SoundCategory.PLAYERS, 0.6f, left <= 0 ? 0.7f : 1.0f);
                }
            }
        }
        if (suffocating) {
            player.setAir(player.getAir() - 1);
            if (player.getAir() <= -20) {
                player.setAir(0);
                player.damage(ModDamageTypes.of(world, ModDamageTypes.VACUUM), SUFFOCATION_DAMAGE);
            }
        }
        if (data.suffocating != suffocating) {
            data.suffocating = suffocating;
            changed = true;
        }

        if (!exempt && time % 20 == 0) {
            changed |= radiation(world, player, data);
        }
        if (!exempt && time % 40 == 0 && !ModDimensions.hasAir(world)) {
            temperatureDamage(world, player, data, suit);
        }
        if (changed) {
            ModComponents.LIFE_SUPPORT.sync(player);
        }
    }

    private static boolean updateEnvironment(ServerWorld world, ServerPlayerEntity player, LifeSupportComponent data, boolean suit) {
        BlockPos eyes = BlockPos.ofFloored(player.getEyePos());
        boolean pressurized = pressurized(player);
        boolean breathable = ModDimensions.hasAir(world) || pressurized;
        int temperature = temperature(world, eyes, pressurized);
        float rate = radiationRate(world, eyes, suit, player.getVehicle() instanceof RocketEntity);
        boolean changed = breathable != data.breathable || temperature != data.temperature || rate != data.doseRate;
        data.breathable = breathable;
        data.temperature = temperature;
        data.doseRate = rate;
        return changed;
    }

    private static boolean radiation(ServerWorld world, ServerPlayerEntity player, LifeSupportComponent data) {
        float before = data.dose;
        if (data.doseRate > 0) {
            data.dose = Math.min(DOSE_MAX, data.dose + data.doseRate);
        } else {
            // Организм понемногу восстанавливается в укрытии
            data.dose = Math.max(0, data.dose - 0.5f);
        }
        if (data.dose >= DOSE_HUNGER) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 60, 0, true, false, true));
        }
        if (data.dose >= DOSE_WEAKNESS) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, true, false, true));
        }
        if (data.dose >= DOSE_SICKNESS) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0, true, false, true));
            if (world.getTime() % 60 == 0) {
                player.damage(ModDamageTypes.of(world, ModDamageTypes.RADIATION), 1);
            }
        }
        return (int) before != (int) data.dose;
    }

    private static void temperatureDamage(ServerWorld world, ServerPlayerEntity player, LifeSupportComponent data, boolean suit) {
        int min = suit ? SpacesuitItem.MIN_TEMPERATURE : BARE_MIN_TEMPERATURE;
        int max = suit ? SpacesuitItem.MAX_TEMPERATURE : BARE_MAX_TEMPERATURE;
        float amount = suit ? 1 : 2;
        if (data.temperature > max) {
            player.damage(ModDamageTypes.of(world, ModDamageTypes.HEAT), amount);
        } else if (data.temperature < min) {
            player.damage(ModDamageTypes.of(world, ModDamageTypes.COLD), amount);
            // Иней по краям экрана, как в рыхлом снегу
            player.setFrozenTicks(Math.max(player.getFrozenTicks(), 130));
        }
    }
}
