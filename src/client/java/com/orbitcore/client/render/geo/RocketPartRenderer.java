package com.orbitcore.client.render.geo;

import com.orbitcore.OrbitCore;
import com.orbitcore.rocket.RocketPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Объёмная модель одного модуля ракеты. Модели лежат в assets/orbitcore/geo/rocket,
 * их строит tools/models/generate.py (там же — проекты Blockbench).
 */
public final class RocketPartRenderer extends GeoObjectRenderer<RocketPartRenderer.Part> {
    private static final Map<String, RocketPartRenderer> RENDERERS = new HashMap<>();

    private final Part part;

    private RocketPartRenderer(String model) {
        super(new PartModel());
        this.part = new Part(model);
    }

    public static RocketPartRenderer of(RocketPart part) {
        return of(part.id());
    }

    /** Модель по имени файла: модуль целиком или его часть, например {@code engine_fakel_bell}. */
    public static RocketPartRenderer of(String model) {
        return RENDERERS.computeIfAbsent(model, RocketPartRenderer::new);
    }

    /**
     * Нарисовать модуль: начало координат — центр его основания.
     * GeckoLib сдвигает объект на полблока, как блок; здесь этот сдвиг снимается.
     */
    public void draw(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.translate(-0.5f, -0.51f, -0.5f);
        this.animatable = part;
        defaultRender(matrices, part, consumers, null, null, 0, 0, light);
        matrices.pop();
    }

    /** Модуль как «анимируемый» объект GeckoLib; анимаций у модулей нет. */
    public static final class Part implements GeoAnimatable {
        private final String model;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        Part(String model) {
            this.model = model;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object object) {
            return 0;
        }
    }

    private static final class PartModel extends GeoModel<Part> {
        @Override
        public Identifier getModelResource(Part part) {
            return OrbitCore.id("geo/rocket/" + part.model + ".geo.json");
        }

        @Override
        public Identifier getTextureResource(Part part) {
            return OrbitCore.id("textures/rocket/" + part.model + ".png");
        }

        @Override
        public Identifier getAnimationResource(Part part) {
            return OrbitCore.id("animations/static.animation.json");
        }
    }
}
