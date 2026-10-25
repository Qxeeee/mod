package com.orbitcore.client.render;

import com.orbitcore.entity.RocketEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

public class RocketEntityRenderer extends EntityRenderer<RocketEntity> {
    public RocketEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 0.7f;
    }

    @Override
    public void render(RocketEntity rocket, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        RocketModelRenderer.render(rocket.design(), matrices, consumers, light, rocket.getWorld());
        super.render(rocket, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(RocketEntity rocket) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
