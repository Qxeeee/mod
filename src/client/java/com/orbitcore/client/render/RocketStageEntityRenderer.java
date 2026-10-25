package com.orbitcore.client.render;

import com.orbitcore.entity.RocketStageEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Отработавшая ступень кувыркается вокруг своей середины.
 */
public class RocketStageEntityRenderer extends EntityRenderer<RocketStageEntity> {
    public RocketStageEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(RocketStageEntity stage, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float half = stage.design().height() / 2f;
        matrices.push();
        matrices.translate(0, half, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(stage.tumble(tickDelta)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(stage.tumble(tickDelta) * 0.3f));
        matrices.translate(0, -half, 0);
        RocketModelRenderer.render(stage.design(), matrices, consumers, light, stage.getWorld());
        matrices.pop();
        super.render(stage, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(RocketStageEntity stage) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
