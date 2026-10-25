package com.orbitcore.client.flight;

import com.orbitcore.client.story.VoiceLines;
import com.orbitcore.OrbitCore;
import com.orbitcore.registry.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Метеорит как сцена из фильма. На подлёте компьютер скафандра пищит тревогой
 * и считает секунды до удара, на грунте мерцает отметка, куда он упадёт.
 * Сам удар в вакууме не слышен: только толчок через подошвы, тряска, вспышка,
 * а если близко — звон в ушах, и на несколько секунд всё вокруг глохнет.
 */
public final class MeteorFx {
    private static final Vector3f MARK = new Vector3f(1.0f, 0.35f, 0.1f);

    @Nullable
    private static Vec3d target;
    private static int left;
    private static int total;
    private static float flash;
    /** Сколько тиков ещё всё глохнет после близкого удара. */
    private static int deafTicks;
    private static int deafTotal = 1;

    private MeteorFx() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MeteorFx::tick);
        HudRenderCallback.EVENT.register(MeteorFx::render);
    }

    public static void warn(Vec3d impact, int ticks) {
        target = impact;
        left = ticks;
        total = Math.max(1, ticks);
        VoiceLines.play(MinecraftClient.getInstance(), "meteor_warning");
    }

    public static void impact(Vec3d center, int size) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        target = null;
        double d = Math.sqrt(player.squaredDistanceTo(center));
        float power = size / 4.0f;
        QuakeShake.start(MathHelper.clamp((2.6f - (float) d / 45f) * power, 0.25f, 3.0f), 40 + size * 6);
        play(client, ModSounds.METEOR_GROUND_IMPACT, MathHelper.clamp(1.2f - (float) d / 180f, 0.15f, 1.0f), 0.85f + 0.1f / power);
        if (d < 110) {
            flash = Math.max(flash, (float) (1 - d / 110) * 0.9f);
        }
        if (d < 40) {
            play(client, ModSounds.METEOR_TINNITUS, 0.25f + 0.5f * (float) (1 - d / 40), 1.0f);
            deafTotal = 90;
            deafTicks = deafTotal;
        }
    }

    /** Множитель громкости остальных звуков: после близкого удара они глохнут и медленно возвращаются. */
    public static float duck(SoundInstance sound) {
        if (deafTicks <= 0) {
            return 1.0f;
        }
        String path = sound.getId().getPath();
        if (OrbitCore.MOD_ID.equals(sound.getId().getNamespace()) && path.startsWith("event.meteor.")) {
            return 1.0f;
        }
        float recovery = 1 - deafTicks / (float) deafTotal;
        return 0.15f + 0.85f * recovery * recovery;
    }

    private static void tick(MinecraftClient client) {
        if (client.isPaused()) {
            return;
        }
        flash *= 0.86f;
        if (deafTicks > 0) {
            deafTicks--;
        }
        if (target == null || client.world == null || client.player == null) {
            return;
        }
        if (--left < 0) {
            target = null;
            return;
        }
        // Тревога скафандра: чем ближе удар, тем чаще писк
        int period = left < 30 ? 4 : left < 60 ? 8 : 14;
        if (left % period == 0) {
            play(client, ModSounds.SUIT_ALARM, 0.45f, left < 30 ? 1.25f : 1.0f);
        }
        // Отметка на грунте: мерцающее кольцо там, куда он упадёт
        if (left < total - 10) {
            for (int i = 0; i < 6; i++) {
                double angle = client.world.random.nextDouble() * Math.PI * 2;
                double r = 2.5 + client.world.random.nextDouble() * 0.5;
                client.world.addParticle(new DustParticleEffect(MARK, 1.6f), target.x + Math.cos(angle) * r, target.y + 0.2,
                        target.z + Math.sin(angle) * r, 0, 0.02, 0);
            }
        }
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        if (flash > 0.02f) {
            context.fill(0, 0, w, h, (int) (flash * 255) << 24 | 0xFFF4E0);
        }
        if (target == null || client.player == null || client.options.hudHidden) {
            return;
        }
        double dx = target.x - client.player.getX();
        double dz = target.z - client.player.getZ();
        float bearing = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        int sector = Math.floorMod(Math.round(MathHelper.wrapDegrees(bearing - client.player.getYaw()) / 45.0f), 8);
        String arrow = new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[sector];
        Text text = Text.translatable("hud.orbitcore.meteor", String.format("%.1f", Math.max(0, left) / 20.0f),
                Math.round(Math.sqrt(dx * dx + dz * dz)), arrow);
        int width = client.textRenderer.getWidth(text);
        int x = (w - width) / 2;
        int y = h / 4 - 16;
        int pulse = (int) (0x70 + 0x60 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / (left < 30 ? 60.0 : 140.0))));
        context.fill(x - 5, y - 4, x + width + 5, y + 12, pulse << 24 | 0x501008);
        context.drawText(client.textRenderer, text, x, y, 0xFFFFB070, false);
        // Полоса обратного отсчёта
        int bar = (int) (width * Math.max(0, left) / (float) total);
        context.fill(x, y + 10, x + bar, y + 11, 0xFFFF6A30);
    }

    private static void play(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        client.getSoundManager().play(new PositionedSoundInstance(sound.getId(), SoundCategory.AMBIENT, volume, pitch,
                SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
    }
}
