package com.orbitcore.client.science;

import com.orbitcore.science.SpaceProgram;

/**
 * Копия космической программы на клиенте. Обновляется пакетом с сервера
 * после каждого изменения; экраны только читают её.
 */
public final class ClientSpaceProgram {
    private static SpaceProgram current = new SpaceProgram();

    private ClientSpaceProgram() {
    }

    public static SpaceProgram get() {
        return current;
    }

    public static void set(SpaceProgram program) {
        current = program;
    }

    /** При выходе из мира забываем программу, чтобы не показать её в другом мире. */
    public static void reset() {
        current = new SpaceProgram();
    }
}
