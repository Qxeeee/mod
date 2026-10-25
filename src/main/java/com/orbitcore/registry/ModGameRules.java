package com.orbitcore.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

/**
 * Правила игры мода. Меняются командой {@code /gamerule}.
 */
public final class ModGameRules {
    /**
     * Режим «Инженер» (по умолчанию): честный расчёт топлива и ручная посадка на Луну.
     * {@code false} — режим «Пилот»: запас топлива прощает 10 %, посадку ведёт автопилот.
     */
    public static final GameRules.Key<GameRules.BooleanRule> ENGINEER_MODE =
            GameRuleRegistry.register("orbitcoreEngineerMode", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

    private ModGameRules() {
    }

    public static void register() {
    }
}
