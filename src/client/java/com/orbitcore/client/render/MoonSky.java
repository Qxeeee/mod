package com.orbitcore.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.orbitcore.OrbitCore;
import com.orbitcore.world.ModDimensions;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Небо Луны: без атмосферы оно чёрное и днём, звёзды видны всегда, Солнце —
 * ослепительный диск, а Земля висит на одном месте и меняет фазы.
 */
public final class MoonSky {
    private static final Identifier SUN = OrbitCore.id("textures/environment/vacuum_sun.png");
    private static final Identifier EARTH = OrbitCore.id("textures/environment/earth_phases.png");
    private static final float[][] STARS = stars();

    private MoonSky() {
    }

    public static void register() {
        DimensionRenderingRegistry.registerDimensionEffects(OrbitCore.id("moon"), new Effects());
        DimensionRenderingRegistry.registerSkyRenderer(ModDimensions.MOON, MoonSky::render);
        DimensionRenderingRegistry.registerCloudRenderer(ModDimensions.MOON, context -> {
        });
        DimensionRenderingRegistry.registerWeatherRenderer(ModDimensions.MOON, context -> {
        });
    }

    private static void render(WorldRenderContext context) {
        ClientWorld world = context.world();
        MatrixStack matrices = context.matrixStack();
        float tickDelta = context.tickDelta();
        float skyAngle = world.getSkyAngle(tickDelta);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngle * 360.0f));
        Matrix4f sky = matrices.peek().getPositionMatrix();

        // Звёзды: днём чуть тусклее — глаз ослеплён Солнцем и залитой светом поверхностью
        float daylight = MathHelper.clamp(MathHelper.cos(skyAngle * MathHelper.TAU) * 2 + 0.5f, 0, 1);
        float starBrightness = 1.0f - daylight * 0.45f;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        // Звёзды видны с любой стороны: без отсечения задних граней
        RenderSystem.disableCull();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (float[] star : STARS) {
            float x = star[0];
            float y = star[1];
            float z = star[2];
            float size = star[3];
            float b = star[4] * starBrightness;
            // Квадрат, развёрнутый к центру сферы
            Vec3d up = Math.abs(y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
            Vec3d n = new Vec3d(x, y, z);
            Vec3d u = n.crossProduct(up).normalize().multiply(size);
            Vec3d v = n.crossProduct(u).normalize().multiply(size);
            for (int corner = 0; corner < 4; corner++) {
                double su = corner == 0 || corner == 3 ? -1 : 1;
                double sv = corner < 2 ? -1 : 1;
                buffer.vertex(sky, (float) (x * 100 + u.x * su + v.x * sv), (float) (y * 100 + u.y * su + v.y * sv),
                        (float) (z * 100 + u.z * su + v.z * sv)).color(b, b, b, 1.0f).next();
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();

        // Солнце: без атмосферы его ничто не рассеивает — резкий белый диск и тонкая корона
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, SUN);
        RenderSystem.blendFuncSeparate(770, 1, 1, 0);
        float s = 34.0f;
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(sky, -s, 100.0f, -s).texture(0, 0).next();
        buffer.vertex(sky, s, 100.0f, -s).texture(1, 0).next();
        buffer.vertex(sky, s, 100.0f, s).texture(1, 1).next();
        buffer.vertex(sky, -s, 100.0f, s).texture(0, 1).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.defaultBlendFunc();
        matrices.pop();

        // Земля не движется по небу Луны: Луна всегда повёрнута к ней одной стороной
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-35.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-52.0f));
        Matrix4f earth = matrices.peek().getPositionMatrix();
        int phase = (Math.round(skyAngle * 8) + 4) % 8;
        float u0 = phase / 8.0f;
        float u1 = (phase + 1) / 8.0f;
        float e = 13.0f;
        RenderSystem.setShaderTexture(0, EARTH);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(earth, -e, 100.0f, -e).texture(u0, 0).next();
        buffer.vertex(earth, e, 100.0f, -e).texture(u1, 0).next();
        buffer.vertex(earth, e, 100.0f, e).texture(u1, 1).next();
        buffer.vertex(earth, -e, 100.0f, e).texture(u0, 1).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static float[][] stars() {
        Random random = new Random(1969L);
        float[][] stars = new float[2400][];
        int n = 0;
        while (n < stars.length) {
            float x = random.nextFloat() * 2 - 1;
            float y = random.nextFloat() * 2 - 1;
            float z = random.nextFloat() * 2 - 1;
            float d = x * x + y * y + z * z;
            if (d > 1 || d < 0.01f) {
                continue;
            }
            float len = (float) Math.sqrt(d);
            float bright = (float) Math.pow(random.nextFloat(), 2.5);
            stars[n++] = new float[]{x / len, y / len, z / len, 0.12f + bright * 0.22f, 0.35f + bright * 0.65f};
        }
        return stars;
    }

    /** Эффекты измерения: туман и фон чёрные, облаков и закатов нет. */
    private static class Effects extends DimensionEffects {
        Effects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
            return Vec3d.ZERO;
        }

        @Override
        public boolean useThickFog(int camX, int camY) {
            return false;
        }

        @Nullable
        @Override
        public float[] getFogColorOverride(float skyAngle, float tickDelta) {
            return null;
        }
    }
}
