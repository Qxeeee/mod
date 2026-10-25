package com.orbitcore.client.render;

import com.orbitcore.OrbitCore;
import com.orbitcore.entity.WatcherEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Наблюдатель выглядит как космонавт «Зари»: пыльный скафандр и чёрный визор. */
public class WatcherEntityRenderer extends MobEntityRenderer<WatcherEntity, PlayerEntityModel<WatcherEntity>> {
    private static final Identifier TEXTURE = OrbitCore.id("textures/entity/watcher.png");

    public WatcherEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.4f);
    }

    @Override
    public Identifier getTexture(WatcherEntity watcher) {
        return TEXTURE;
    }

    @Override
    protected boolean hasLabel(WatcherEntity watcher) {
        return false;
    }
}
