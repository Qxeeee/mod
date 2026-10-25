package com.orbitcore;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Общая часть мода: работает и на клиенте, и на сервере.
 * Здесь регистрируются блоки, предметы, сущности, измерения и сетевые пакеты.
 */
public class OrbitCore implements ModInitializer {
    public static final String MOD_ID = "orbitcore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Идентификатор в пространстве имён мода: {@code orbitcore:<path>}. */
    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("OrbitCore initialized");
    }
}
