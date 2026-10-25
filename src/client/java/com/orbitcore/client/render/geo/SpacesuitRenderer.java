package com.orbitcore.client.render.geo;

import com.orbitcore.OrbitCore;
import com.orbitcore.item.SpacesuitItem;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Объёмный скафандр на игроке: шлем с золотым визором, ранец, перчатки, ботинки.
 * Модель и текстуру строит tools/models/generate.py.
 */
public class SpacesuitRenderer extends GeoArmorRenderer<SpacesuitItem> {
    public SpacesuitRenderer() {
        super(new GeoModel<>() {
            @Override
            public Identifier getModelResource(SpacesuitItem item) {
                return OrbitCore.id("geo/armor/spacesuit.geo.json");
            }

            @Override
            public Identifier getTextureResource(SpacesuitItem item) {
                return OrbitCore.id("textures/armor/spacesuit.png");
            }

            @Override
            public Identifier getAnimationResource(SpacesuitItem item) {
                return OrbitCore.id("animations/static.animation.json");
            }
        });
    }

    /** Подключить рендерер к предметам скафандра. */
    public static void register() {
        SpacesuitItem.clientRenderer = consumer -> consumer.accept(new RenderProvider() {
            private SpacesuitRenderer renderer;

            @Override
            public BipedEntityModel<LivingEntity> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                                       BipedEntityModel<LivingEntity> original) {
                if (renderer == null) {
                    renderer = new SpacesuitRenderer();
                }
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        });
    }
}
