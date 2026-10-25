package com.orbitcore.client.render;

import com.orbitcore.client.render.geo.RocketPartRenderer;
import com.orbitcore.entity.RoverEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Луноход: корпус одной моделью, четыре колеса отдельно — они крутятся на ходу. */
public class RoverEntityRenderer extends EntityRenderer<RoverEntity> {
    /** Оси колёс в пикселях модели: x, y, z. */
    private static final float[][] WHEELS = {{18, 7, -16}, {-18, 7, -16}, {18, 7, 16}, {-18, 7, 16}};

    public RoverEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 1.3f;
    }

    @Override
    public void render(RoverEntity rover, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        // У модели перёд смотрит на −Z, у сущности — на +Z
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        RocketPartRenderer.of("rover_body").draw(matrices, consumers, light);
        float spin = rover.wheelSpin(tickDelta);
        for (float[] wheel : WHEELS) {
            matrices.push();
            matrices.translate(wheel[0] / 16f, wheel[1] / 16f, wheel[2] / 16f);
            matrices.multiply(RotationAxis.NEGATIVE_X.rotation(spin));
            RocketPartRenderer.of("rover_wheel").draw(matrices, consumers, light);
            matrices.pop();
        }
        matrices.pop();
        super.render(rover, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(RoverEntity rover) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
