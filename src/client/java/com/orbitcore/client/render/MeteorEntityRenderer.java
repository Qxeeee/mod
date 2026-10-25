package com.orbitcore.client.render;

import com.orbitcore.entity.MeteorEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.world.ModDimensions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Кувыркающийся раскалённый обломок с огненным хвостом. */
public class MeteorEntityRenderer extends EntityRenderer<MeteorEntity> {
    public MeteorEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(MeteorEntity meteor, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float spin = (meteor.age + tickDelta) * 25;
        matrices.push();
        matrices.translate(0, 0.5, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin * 0.7f));
        matrices.scale(2.2f, 2.2f, 2.2f);
        matrices.translate(-0.5, -0.5, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(ModBlocks.METEORITE.getDefaultState(), matrices,
                consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        renderTail(meteor, matrices, consumers);
        super.render(meteor, yaw, tickDelta, matrices, consumers, light);
    }

    /** Светящийся хвост против направления полёта: два скрещённых сужающихся луча. */
    private static void renderTail(MeteorEntity meteor, MatrixStack matrices, VertexConsumerProvider consumers) {
        Vec3d velocity = meteor.getVelocity();
        if (velocity.lengthSquared() < 1.0E-4) {
            return;
        }
        Vec3d back = velocity.normalize().multiply(-14);
        Vec3d side = back.crossProduct(new Vec3d(0, 1, 0));
        if (side.lengthSquared() < 1.0E-4) {
            side = new Vec3d(1, 0, 0);
        }
        side = side.normalize();
        Vec3d up = side.crossProduct(back).normalize();
        boolean air = ModDimensions.hasAir(meteor.getWorld());
        int r = 255;
        int g = air ? 150 : 220;
        int b = air ? 60 : 255;
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLightning());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (Vec3d axis : new Vec3d[]{side, up}) {
            Vec3d a = axis.multiply(0.9);
            buffer.vertex(matrix, (float) a.x, (float) (a.y + 0.5), (float) a.z).color(r, g, b, 220).next();
            buffer.vertex(matrix, (float) -a.x, (float) (-a.y + 0.5), (float) -a.z).color(r, g, b, 220).next();
            buffer.vertex(matrix, (float) back.x, (float) (back.y + 0.5), (float) back.z).color(r, g / 2, b / 3, 0).next();
            buffer.vertex(matrix, (float) back.x, (float) (back.y + 0.5), (float) back.z).color(r, g / 2, b / 3, 0).next();
        }
    }

    @Override
    public Identifier getTexture(MeteorEntity meteor) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
