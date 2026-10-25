package com.orbitcore.client.render;

import com.orbitcore.block.HoloTabletBlock;
import com.orbitcore.block.entity.HoloTabletBlockEntity;
import com.orbitcore.story.HoloTablets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Голограмма над живым планшетом: полупрозрачная бирюзовая табличка с заголовком
 * записи, медленно покачивается и мерцает. Иссякший планшет молчит.
 */
public class HoloTabletRenderer implements BlockEntityRenderer<HoloTabletBlockEntity> {
    private final TextRenderer font;

    public HoloTabletRenderer(BlockEntityRendererFactory.Context context) {
        font = context.getTextRenderer();
    }

    @Override
    public void render(HoloTabletBlockEntity tablet, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (!tablet.getCachedState().get(HoloTabletBlock.ACTIVE) || tablet.log().isEmpty() || tablet.getWorld() == null) {
            return;
        }
        float time = tablet.getWorld().getTime() + tickDelta;
        float flicker = 0.75f + 0.25f * MathHelper.sin(time * 0.9f) * MathHelper.sin(time * 0.37f);
        if ((long) (time * 7 + tablet.getPos().asLong()) % 53 == 0) {
            flicker *= 0.3f;
        }
        matrices.push();
        matrices.translate(0.5, 1.15 + MathHelper.sin(time / 20f) * 0.05, 0.5);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.024f, -0.024f, 0.024f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int alpha = (int) (flicker * 230);
        Text title = Text.translatable(HoloTablets.titleKey(tablet.log()));
        Text marker = Text.translatable("holo.orbitcore.signal_marker");
        int background = (int) (flicker * 70) << 24 | 0x0A3040;
        font.draw(title, -font.getWidth(title) / 2f, 0, alpha << 24 | 0x7CF4FF, false, matrix, consumers,
                TextRenderer.TextLayerType.NORMAL, background, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        font.draw(marker, -font.getWidth(marker) / 2f, 11, (alpha / 2) << 24 | 0x7CF4FF, false, matrix, consumers,
                TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    @Override
    public int getRenderDistance() {
        return 48;
    }
}
