package com.orbitcore.gametest;

import com.orbitcore.OrbitCore;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * Проверки на настоящем сервере: gradlew runGametest.
 */
public class OrbitCoreGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modAndLibrariesLoad(TestContext context) {
        FabricLoader loader = FabricLoader.getInstance();
        // Satin здесь не проверяем: он только для клиента и на сервере не загружается
        for (String modId : new String[]{OrbitCore.MOD_ID, "geckolib", "cardinal-components-base", "team_reborn_energy"}) {
            context.assertTrue(loader.isModLoaded(modId), modId + " is not loaded");
        }
        context.complete();
    }
}
