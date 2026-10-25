package com.orbitcore.rocket;

import org.jetbrains.annotations.Nullable;

/**
 * Модули ракеты. Ракета собирается из них снизу вверх; разделитель делит её на ступени.
 *
 * <p>Единицы: масса — тонны, тяга — кН, удельный импульс — секунды, топливо — мБ
 * (1 мБ = 1 кг; бак вмещает столько-то керосина и столько же кислорода).
 *
 * @param heightPx высота модуля в пикселях модели (16 = один блок); 0 — модуль крепится вокруг предыдущего
 */
public enum RocketPart {
    ENGINE_ISKRA("engine_iskra", Kind.ENGINE, 16, 1.0, 120, 290, 0, 4),
    ENGINE_FAKEL("engine_fakel", Kind.ENGINE, 16, 2.5, 400, 310, 0, 4),
    FUEL_TANK_SMALL("fuel_tank_small", Kind.TANK, 16, 0.5, 0, 0, 2000, 1),
    FUEL_TANK_LARGE("fuel_tank_large", Kind.TANK, 32, 1.5, 0, 0, 6000, 1),
    STAGE_DECOUPLER("stage_decoupler", Kind.DECOUPLER, 4, 0.3, 0, 0, 0, 1),
    CAPSULE("capsule_kaplya", Kind.CAPSULE, 24, 2.0, 0, 0, 0, 1),
    FINS("rocket_fins", Kind.FINS, 0, 0.2, 0, 0, 0, 1),
    LANDING_LEGS("landing_legs", Kind.LEGS, 0, 0.4, 0, 0, 0, 1);

    public enum Kind {
        ENGINE, TANK, DECOUPLER, CAPSULE, FINS, LEGS
    }

    private final String id;
    private final Kind kind;
    private final int heightPx;
    private final double dryMass;
    private final double thrust;
    private final double isp;
    private final int propellant;
    private final int maxStack;

    RocketPart(String id, Kind kind, int heightPx, double dryMass, double thrust, double isp, int propellant, int maxStack) {
        this.id = id;
        this.kind = kind;
        this.heightPx = heightPx;
        this.dryMass = dryMass;
        this.thrust = thrust;
        this.isp = isp;
        this.propellant = propellant;
        this.maxStack = maxStack;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public int heightPx() {
        return heightPx;
    }

    /** Сухая масса одного модуля, т. */
    public double dryMass() {
        return dryMass;
    }

    /** Тяга одного двигателя, кН. */
    public double thrust() {
        return thrust;
    }

    /** Удельный импульс двигателя, с. */
    public double isp() {
        return isp;
    }

    /** Сколько мБ керосина (и столько же кислорода) вмещает бак. */
    public int propellant() {
        return propellant;
    }

    /** Сколько штук можно поставить в одну ячейку: двигатели ставятся связкой до четырёх. */
    public int maxStack() {
        return maxStack;
    }

    /** Модуль крепится вокруг предыдущего и не добавляет высоты (стабилизаторы, опоры). */
    public boolean isRadial() {
        return heightPx == 0;
    }

    @Nullable
    public static RocketPart byId(String id) {
        for (RocketPart part : values()) {
            if (part.id.equals(id)) {
                return part;
            }
        }
        return null;
    }
}
