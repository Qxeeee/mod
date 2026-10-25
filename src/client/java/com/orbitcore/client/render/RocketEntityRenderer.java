package com.orbitcore.client.render;

import com.orbitcore.OrbitCore;
import com.orbitcore.entity.RocketEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class RocketEntityRenderer extends EntityRenderer<RocketEntity> {
    /** Отдельная модель купола: загружается без блока и предмета. */
    public static final Identifier PARACHUTE = OrbitCore.id("misc/parachute");

    public RocketEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 0.7f;
    }

    @Override
    public void render(RocketEntity rocket, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        RocketModelRenderer.render(rocket.design(), matrices, consumers, light, rocket.getWorld());
        if (rocket.parachute()) {
            renderParachute(rocket, tickDelta, matrices, consumers, light);
        }
        super.render(rocket, yaw, tickDelta, matrices, consumers, light);
    }

    /** Купол над капсулой, слегка раскачивается на стропах. */
    private void renderParachute(RocketEntity rocket, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        BakedModel model = MinecraftClient.getInstance().getBakedModelManager().getModel(PARACHUTE);
        if (model == null) {
            return;
        }
        float time = rocket.age + tickDelta;
        matrices.push();
        matrices.translate(0, rocket.design().height(), 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(time * 0.05) * 6));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.cos(time * 0.04) * 5));
        matrices.scale(4f, 4f, 4f);
        matrices.translate(-0.5, 0, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager().getModelRenderer().render(matrices.peek(),
                consumers.getBuffer(RenderLayer.getCutout()), null, model, 1f, 1f, 1f, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    @Override
    public Identifier getTexture(RocketEntity rocket) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
