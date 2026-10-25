package com.orbitcore.client.render;

import com.orbitcore.registry.ModItems;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Рисует модульную ракету из 3D-моделей её модулей (тех же, что у предметов).
 * Используется и для ракеты в мире, и для превью на сборочном стенде.
 */
public final class RocketModelRenderer {
    /** Смещения двигателей в связке из 2, 3 и 4 штук. */
    private static final float[][][] CLUSTERS = {
            {{0, 0}},
            {{-0.26f, 0}, {0.26f, 0}},
            {{0, -0.27f}, {-0.25f, 0.16f}, {0.25f, 0.16f}},
            {{-0.25f, -0.25f}, {0.25f, -0.25f}, {-0.25f, 0.25f}, {0.25f, 0.25f}}};

    private RocketModelRenderer() {
    }

    /** Ракета снизу вверх; начало координат — центр основания первой ступени. */
    public static void render(RocketDesign design, MatrixStack matrices, VertexConsumerProvider consumers, int light, @Nullable World world) {
        ItemRenderer items = MinecraftClient.getInstance().getItemRenderer();
        float y = 0;
        float previousY = 0;
        for (RocketDesign.Entry entry : design.parts()) {
            RocketPart part = entry.part();
            ItemStack stack = new ItemStack(itemFor(part));
            if (part.isRadial()) {
                // Стабилизаторы и опоры крепятся вокруг модуля, стоящего под ними
                renderPart(items, stack, matrices, consumers, light, world, previousY, 0, 0, 1f);
                continue;
            }
            if (part.kind() == RocketPart.Kind.ENGINE && entry.count() > 1) {
                float[][] cluster = CLUSTERS[Math.min(entry.count(), 4) - 1];
                float scale = entry.count() == 4 ? 0.5f : 0.55f;
                for (float[] offset : cluster) {
                    renderPart(items, stack, matrices, consumers, light, world, y, offset[0], offset[1], scale);
                }
            } else {
                renderPart(items, stack, matrices, consumers, light, world, y, 0, 0, 1f);
            }
            previousY = y;
            y += part.heightPx() / 16f;
        }
    }

    private static void renderPart(ItemRenderer items, ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers,
                                   int light, @Nullable World world, float y, float dx, float dz, float widthScale) {
        matrices.push();
        matrices.translate(dx, y, dz);
        matrices.scale(widthScale, 1f, widthScale);
        // Модель предмета центрирована в нуле: поднимаем её, чтобы низ встал на y
        matrices.translate(0, 0.5f, 0);
        items.renderItem(stack, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, consumers, world, 0);
        matrices.pop();
    }

    /** Ракета в интерфейсе: вращается вокруг оси и вписана в прямоугольник. */
    public static void drawInGui(DrawContext context, RocketDesign design, int x, int y, int width, int height, float spin) {
        if (design.parts().isEmpty()) {
            return;
        }
        float pixelsPerBlock = Math.min(height / (design.height() + 0.4f), width / 1.6f);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + width / 2f, y + height - 4, 150);
        matrices.multiplyPositionMatrix(new Matrix4f().scaling(pixelsPerBlock, pixelsPerBlock, -pixelsPerBlock));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        DiffuseLighting.method_34742();
        render(design, matrices, context.getVertexConsumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE, MinecraftClient.getInstance().world);
        context.draw();
        matrices.pop();
        DiffuseLighting.enableGuiDepthLighting();
    }

    public static Item itemFor(RocketPart part) {
        return switch (part) {
            case ENGINE_ISKRA -> ModItems.ENGINE_ISKRA;
            case ENGINE_FAKEL -> ModItems.ENGINE_FAKEL;
            case FUEL_TANK_SMALL -> ModItems.FUEL_TANK_SMALL;
            case FUEL_TANK_LARGE -> ModItems.FUEL_TANK_LARGE;
            case STAGE_DECOUPLER -> ModItems.STAGE_DECOUPLER;
            case CAPSULE -> ModItems.CAPSULE_KAPLYA;
            case FINS -> ModItems.ROCKET_FINS;
            case LANDING_LEGS -> ModItems.LANDING_LEGS;
        };
    }
}
