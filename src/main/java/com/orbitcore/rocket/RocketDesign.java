package com.orbitcore.rocket;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Конструкция ракеты: модули снизу вверх и всё, что из них следует — ступени,
 * масса, тяговооружённость и запас характеристической скорости (дельта-V).
 *
 * <p>Физика упрощённая, но честная: формула Циолковского для каждой ступени,
 * ступени работают снизу вверх, отработавшая ступень сбрасывается.
 */
public final class RocketDesign {
    public static final double G0 = 9.81;
    public static final int MAX_PARTS = 10;

    private final List<Entry> parts;
    private final List<Stage> stages;
    private final Set<Problem> problems = EnumSet.noneOf(Problem.class);

    public record Entry(RocketPart part, int count) {
    }

    /**
     * Ступень ракеты.
     *
     * @param propellant мБ керосина (и столько же кислорода) в баках ступени
     */
    public record Stage(double dryMass, int propellant, double thrust, double isp, boolean hasEngine, boolean hasTank) {
        /** Масса топлива ступени при заполнении {@code fill} (0–1), т. */
        public double propellantMass(double fill) {
            return 2.0 * propellant * fill / 1000.0;
        }
    }

    /** Что не так с конструкцией. Ошибки не дают собрать ракету, предупреждения — только подсказка. */
    public enum Problem {
        EMPTY(true), NO_ENGINE(true), NO_TANK(true), ENGINE_NOT_AT_BOTTOM(true), CAPSULE_NOT_ON_TOP(true),
        LOW_THRUST(false), NO_CAPSULE(false), NO_FINS(false), NO_LEGS(false);

        private final boolean error;

        Problem(boolean error) {
            this.error = error;
        }

        public boolean isError() {
            return error;
        }

        public String translationKey() {
            return "rocket.orbitcore.problem." + name().toLowerCase();
        }
    }

    /** Куда хватает дельта-V. Числа подобраны под игру и меньше настоящих. */
    public enum Destination {
        SUBORBITAL(1500), EARTH_ORBIT(3000), MOON_LANDING(5000), MOON_RETURN(6500);

        private final int deltaV;

        Destination(int deltaV) {
            this.deltaV = deltaV;
        }

        public int deltaV() {
            return deltaV;
        }

        public String translationKey() {
            return "rocket.orbitcore.destination." + name().toLowerCase();
        }
    }

    public RocketDesign(List<Entry> parts) {
        this.parts = List.copyOf(parts);
        this.stages = buildStages();
        check();
    }

    public List<Entry> parts() {
        return parts;
    }

    public List<Stage> stages() {
        return stages;
    }

    public Set<Problem> problems() {
        return Collections.unmodifiableSet(problems);
    }

    public boolean isValid() {
        return problems.stream().noneMatch(Problem::isError);
    }

    public boolean has(RocketPart.Kind kind) {
        return parts.stream().anyMatch(e -> e.part.kind() == kind);
    }

    /** Высота ракеты в блоках. */
    public float height() {
        int px = parts.stream().mapToInt(e -> e.part.heightPx()).sum();
        return Math.max(1, px) / 16f;
    }

    /** Сколько мБ керосина (и столько же кислорода) нужно, чтобы залить все баки. */
    public int propellantCapacity() {
        return stages.stream().mapToInt(Stage::propellant).sum();
    }

    /** Полная масса при заполнении {@code fill}, т. */
    public double mass(double fill) {
        return stages.stream().mapToDouble(s -> s.dryMass + s.propellantMass(fill)).sum();
    }

    /** Тяговооружённость первой ступени на Земле: больше 1 — ракета оторвётся от стола. */
    public double liftoffTwr(double fill) {
        if (stages.isEmpty()) {
            return 0;
        }
        return stages.get(0).thrust / (mass(fill) * G0);
    }

    /** Дельта-V каждой ступени (м/с) при заполнении {@code fill}. */
    public List<Double> stageDeltaV(double fill) {
        List<Double> result = new ArrayList<>();
        double above = mass(fill);
        for (Stage stage : stages) {
            double full = above;
            double empty = full - stage.propellantMass(fill);
            double dv = stage.hasEngine && empty > 0 && full > empty ? stage.isp * G0 * Math.log(full / empty) : 0;
            result.add(dv);
            above = full - stage.dryMass - stage.propellantMass(fill);
        }
        return result;
    }

    /** Суммарная дельта-V, м/с. */
    public double deltaV(double fill) {
        return stageDeltaV(fill).stream().mapToDouble(Double::doubleValue).sum();
    }

    public boolean canReach(Destination destination, double fill) {
        return isValid() && liftoffTwr(fill) > 1.0 && deltaV(fill) >= destination.deltaV;
    }

    // ------------------------------------------------------------------ разбор

    private List<Stage> buildStages() {
        List<Stage> result = new ArrayList<>();
        List<Entry> current = new ArrayList<>();
        for (Entry entry : parts) {
            current.add(entry);
            if (entry.part.kind() == RocketPart.Kind.DECOUPLER) {
                result.add(stageOf(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            result.add(stageOf(current));
        }
        return result;
    }

    private static Stage stageOf(List<Entry> entries) {
        double dry = 0;
        int propellant = 0;
        double thrust = 0;
        double ispWeighted = 0;
        boolean engine = false;
        boolean tank = false;
        for (Entry e : entries) {
            dry += e.part.dryMass() * e.count;
            propellant += e.part.propellant() * e.count;
            if (e.part.kind() == RocketPart.Kind.ENGINE) {
                engine = true;
                thrust += e.part.thrust() * e.count;
                ispWeighted += e.part.thrust() * e.part.isp() * e.count;
            }
            tank |= e.part.kind() == RocketPart.Kind.TANK;
        }
        return new Stage(dry, propellant, thrust, thrust > 0 ? ispWeighted / thrust : 0, engine, tank);
    }

    private void check() {
        if (parts.isEmpty()) {
            problems.add(Problem.EMPTY);
            return;
        }
        // Каждая ступень начинается с двигателя: иначе струя бьёт в бак
        boolean stageStart = true;
        for (Entry e : parts) {
            if (e.part.isRadial()) {
                continue;
            }
            if (stageStart && e.part.kind() != RocketPart.Kind.ENGINE) {
                problems.add(Problem.ENGINE_NOT_AT_BOTTOM);
            }
            stageStart = e.part.kind() == RocketPart.Kind.DECOUPLER;
        }
        for (Stage stage : stages) {
            if (!stage.hasEngine) {
                problems.add(Problem.NO_ENGINE);
            }
            if (!stage.hasTank) {
                problems.add(Problem.NO_TANK);
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).part.kind() == RocketPart.Kind.CAPSULE) {
                for (int j = i + 1; j < parts.size(); j++) {
                    if (!parts.get(j).part.isRadial()) {
                        problems.add(Problem.CAPSULE_NOT_ON_TOP);
                    }
                }
            }
        }
        if (liftoffTwr(1.0) <= 1.0) {
            problems.add(Problem.LOW_THRUST);
        }
        if (!has(RocketPart.Kind.CAPSULE)) {
            problems.add(Problem.NO_CAPSULE);
        }
        if (!has(RocketPart.Kind.FINS)) {
            problems.add(Problem.NO_FINS);
        }
        if (!has(RocketPart.Kind.LEGS)) {
            problems.add(Problem.NO_LEGS);
        }
    }

    // ------------------------------------------------------------------ сохранение

    public NbtList toNbt() {
        NbtList list = new NbtList();
        for (Entry e : parts) {
            NbtCompound tag = new NbtCompound();
            tag.putString("Part", e.part.id());
            tag.putInt("Count", e.count);
            list.add(tag);
        }
        return list;
    }

    public static RocketDesign fromNbt(NbtList list) {
        List<Entry> parts = new ArrayList<>();
        for (NbtElement element : list) {
            if (element instanceof NbtCompound tag) {
                RocketPart part = RocketPart.byId(tag.getString("Part"));
                if (part != null) {
                    parts.add(new Entry(part, Math.max(1, Math.min(part.maxStack(), tag.getInt("Count")))));
                }
            }
        }
        return new RocketDesign(parts);
    }
}
