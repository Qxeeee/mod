package com.orbitcore.client.sound;

import com.orbitcore.client.flight.FlightCinematics;
import com.orbitcore.item.SpacesuitItem;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.survival.LifeSupport;
import com.orbitcore.survival.LifeSupportComponent;
import com.orbitcore.survival.Oxygen;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import org.jetbrains.annotations.Nullable;

/**
 * Страх пустоты. На Луне за стенами базы тихо, как не бывает на Земле, и в этой тишине
 * слышно то, чего быть не должно: гул, скрип металла, шёпот за спиной, голоса в помехах.
 * Чем дальше от базы, темнее и ночнее, тем гуще всё это. Внутри базы спокойно,
 * только по ночам кто-то иногда стучит снаружи.
 * <p>
 * Всё звучит «в голове», внутри шлема, поэтому не глохнет в вакууме. Громкость —
 * ползунок «Окружение» в настройках звука.
 */
public final class SpaceDread {
    private static final String[] RADIO = {"dread.orbitcore.radio.1", "dread.orbitcore.radio.2", "dread.orbitcore.radio.3",
            "dread.orbitcore.radio.4", "dread.orbitcore.radio.5"};
    private static final Random RANDOM = Random.create();

    @Nullable
    private static LoopSound drone;
    @Nullable
    private static LoopSound hum;
    private static int nextEvent = 900;
    private static int heartbeat;
    private static int breath;
    private static int gazeTicks;
    private static int gazeCooldown;
    /** 0–1: насколько темнеют края экрана, когда смотришь в бездну. */
    private static float gaze;
    /** 0–1: помехи на визоре рядом с Эхом. */
    private static float glitch;
    @Nullable
    private static Vec3d lastSafe;
    /** Где стоит Эхо, если игроку уже известен сигнал. */
    @Nullable
    private static BlockPos echo;

    private SpaceDread() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SpaceDread::tick);
        HudRenderCallback.EVENT.register(SpaceDread::render);
    }

    public static void setEcho(@Nullable BlockPos pos) {
        echo = pos;
    }

    @Nullable
    public static BlockPos echo() {
        return echo;
    }

    /** Насколько игроку сейчас должно быть не по себе, 0–1. */
    public static float dread(MinecraftClient client, ClientPlayerEntity player, LifeSupportComponent data) {
        if (data.breathable) {
            return 0.06f;
        }
        BlockPos pos = player.getBlockPos();
        float skyAngle = client.world.getSkyAngle(1.0f);
        float daylight = MathHelper.clamp(MathHelper.cos(skyAngle * MathHelper.TAU) * 2 + 0.5f, 0, 1);
        float light = Math.max(client.world.getLightLevel(LightType.BLOCK, pos), client.world.getLightLevel(LightType.SKY, pos) * daylight);
        float darkness = 1 - light / 15f;
        float far = lastSafe == null ? 0.5f : MathHelper.clamp((float) (Math.sqrt(player.squaredDistanceTo(lastSafe)) - 48) / 300f, 0, 1);
        float dread = MathHelper.clamp(0.22f + 0.35f * darkness + 0.25f * far + 0.18f * (1 - daylight), 0, 1);
        if (echo != null) {
            double d = Math.sqrt(player.squaredDistanceTo(Vec3d.ofCenter(echo)));
            dread = Math.max(dread, MathHelper.clamp(1 - (float) (d - 30) / 250f, 0, 1));
        }
        return dread;
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.isPaused()) {
            return;
        }
        boolean active = client.world.getRegistryKey() == ModDimensions.MOON && !player.isSpectator() && FlightCinematics.rocket() == null;
        if (!active) {
            fade(drone);
            fade(hum);
            gaze = Math.max(0, gaze - 0.05f);
            glitch = 0;
            return;
        }
        LifeSupportComponent data = LifeSupport.get(player);
        if (data.breathable) {
            lastSafe = player.getPos();
        }
        float dread = dread(client, player, data);
        drone = LoopSound.drive(drone, ModSounds.VOID_DRONE, SoundCategory.AMBIENT, data.breathable ? 0 : dread * dread * 0.6f);
        float humVolume = 0;
        glitch = 0;
        if (echo != null) {
            double d = Math.sqrt(player.squaredDistanceTo(Vec3d.ofCenter(echo)));
            humVolume = MathHelper.clamp(1 - (float) d / 180f, 0, 1) * 0.9f;
            glitch = MathHelper.clamp(1 - (float) (d - 16) / 110f, 0, 1);
            if (RANDOM.nextFloat() < glitch * 0.015f) {
                playInHead(client, ModSounds.ECHO_STATIC, 0.3f + glitch * 0.5f, 0.8f + RANDOM.nextFloat() * 0.4f);
            }
        }
        hum = LoopSound.drive(hum, ModSounds.ECHO_HUM, SoundCategory.AMBIENT, humVolume);

        if (--nextEvent <= 0) {
            randomEvent(client, player, data, dread);
            nextEvent = (int) MathHelper.lerp(dread, 2400, 500) + RANDOM.nextInt(600);
        }
        bodySounds(client, player, data, dread);
        abyssGaze(client, player, data);
    }

    /** Случайное: скрип, шёпот за спиной, голос в эфире, стук в стену базы. */
    private static void randomEvent(MinecraftClient client, ClientPlayerEntity player, LifeSupportComponent data, float dread) {
        float r = RANDOM.nextFloat();
        float skyAngle = client.world.getSkyAngle(1.0f);
        boolean night = MathHelper.cos(skyAngle * MathHelper.TAU) < 0;
        Vec3d look = player.getRotationVec(1.0f);
        if (data.breathable) {
            if (night && r < 0.3f) {
                // Стук в стену с той стороны, куда игрок не смотрит
                Vec3d at = player.getPos().subtract(look.x * 4, 0, look.z * 4);
                playAt(client, ModSounds.VOID_KNOCK, at, 0.9f, 0.9f + RANDOM.nextFloat() * 0.2f);
            } else {
                playInHead(client, ModSounds.VOID_CREAK, 0.25f, 0.8f + RANDOM.nextFloat() * 0.3f);
            }
            return;
        }
        if (r < 0.35f) {
            Vec3d at = player.getPos().add(RANDOM.nextFloat() * 12 - 6, 0, RANDOM.nextFloat() * 12 - 6);
            playAt(client, ModSounds.VOID_CREAK, at, 0.6f, 0.7f + RANDOM.nextFloat() * 0.4f);
        } else if (r < 0.6f && dread > 0.45f) {
            // Шёпот прямо за спиной
            Vec3d at = player.getEyePos().subtract(look.multiply(2.5));
            playAt(client, ModSounds.VOID_WHISPER, at, 0.7f, 0.8f + RANDOM.nextFloat() * 0.25f);
        } else if (r < 0.82f && SpacesuitItem.isWearingHelmet(player)) {
            playInHead(client, ModSounds.VOID_RADIO_GHOST, 0.55f, 0.9f + RANDOM.nextFloat() * 0.2f);
            client.inGameHud.setOverlayMessage(Text.translatable(RADIO[RANDOM.nextInt(RADIO.length)])
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        } else {
            playInHead(client, ModSounds.VOID_SWELL, 0.35f, 0.85f);
        }
    }

    /** Сердце и дыхание: когда кончается воздух, при беге и когда совсем жутко. */
    private static void bodySounds(MinecraftClient client, ClientPlayerEntity player, LifeSupportComponent data, float dread) {
        boolean helmet = SpacesuitItem.isWearingHelmet(player);
        int oxygen = helmet ? Oxygen.total(player) : Integer.MAX_VALUE;
        boolean panic = data.suffocating || oxygen < 60 || player.getHealth() < 6;
        if (--heartbeat <= 0 && (panic || dread > 0.88f)) {
            playInHead(client, ModSounds.VOID_HEARTBEAT, panic ? 0.9f : 0.5f, 1.0f);
            heartbeat = panic ? 15 : 22;
        }
        if (--breath <= 0 && helmet && !data.breathable && (oxygen < 120 || player.isSprinting() || gaze > 0.3f)) {
            boolean fast = player.isSprinting() || oxygen < 60;
            playInHead(client, ModSounds.VOID_BREATH, 0.35f, fast ? 1.2f : 1.0f);
            breath = fast ? 64 : 86;
        }
    }

    /** Если долго смотреть вверх, в чёрное небо без дна, — оно смотрит в ответ. */
    private static void abyssGaze(MinecraftClient client, ClientPlayerEntity player, LifeSupportComponent data) {
        if (gazeCooldown > 0) {
            gazeCooldown--;
        }
        boolean looking = !data.breathable && player.getPitch() < -62 && client.world.isSkyVisible(player.getBlockPos().up());
        gazeTicks = looking ? gazeTicks + 1 : 0;
        if (gazeTicks == 60 && gazeCooldown == 0) {
            playInHead(client, ModSounds.VOID_SWELL, 0.8f, 1.0f);
            gazeCooldown = 1200;
        }
        float target = looking && gazeTicks > 40 ? Math.min(1, (gazeTicks - 40) / 140f) : 0;
        gaze += (target - gaze) * (target > gaze ? 0.02f : 0.08f);
    }

    // ------------------------------------------------------------------ экран

    private static void render(DrawContext context, float tickDelta) {
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        if (gaze > 0.01f) {
            // Края темнеют, будто небо затягивает взгляд
            int band = (int) (Math.min(w, h) * (0.15f + 0.3f * gaze));
            int alpha = (int) (230 * gaze);
            context.fillGradient(0, 0, w, band, alpha << 24, 0);
            context.fillGradient(0, h - band, w, h, 0, alpha << 24);
            for (int i = 0; i < band; i += 2) {
                int a = (int) (alpha * (1 - i / (float) band));
                context.fill(i, 0, i + 2, h, a << 24);
                context.fill(w - i - 2, 0, w - i, h, a << 24);
            }
        }
        if (glitch > 0.02f) {
            // Помехи на визоре: рваные строки, иногда вспышка
            int lines = (int) (glitch * 26);
            for (int i = 0; i < lines; i++) {
                int y = RANDOM.nextInt(h);
                int x = RANDOM.nextInt(w);
                int len = 10 + RANDOM.nextInt(w / 2);
                int shade = 120 + RANDOM.nextInt(135);
                int a = (int) ((30 + RANDOM.nextInt(70)) * glitch);
                context.fill(x, y, Math.min(w, x + len), y + 1, a << 24 | shade << 16 | shade << 8 | shade);
            }
            if (glitch > 0.5f && RANDOM.nextFloat() < 0.01f * glitch) {
                context.fill(0, 0, w, h, (int) (60 * glitch) << 24 | 0xC8D0D8);
            }
        }
    }

    // ------------------------------------------------------------------ звук

    private static void playInHead(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        client.getSoundManager().play(new PositionedSoundInstance(sound.getId(), SoundCategory.AMBIENT, volume, pitch,
                SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
    }

    private static void playAt(MinecraftClient client, SoundEvent sound, Vec3d at, float volume, float pitch) {
        client.getSoundManager().play(new PositionedSoundInstance(sound, SoundCategory.AMBIENT, volume, pitch,
                SoundInstance.createRandom(), at.x, at.y, at.z));
    }

    private static void fade(@Nullable LoopSound sound) {
        LoopSound.drive(sound, ModSounds.VOID_DRONE, SoundCategory.AMBIENT, 0);
    }
}
