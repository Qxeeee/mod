package com.orbitcore.science;

import com.orbitcore.network.OrbitNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Космическая программа сервера: очки науки, открытые тела, исследования и статьи Космопедии.
 * Одна на весь сервер — все игроки работают на общий прогресс.
 *
 * <p>На сервере хранится в сохранении мира, клиенты получают копию после каждого изменения.
 */
public class SpaceProgram extends PersistentState {
    private static final String SAVE_ID = "orbitcore_space_program";
    /** Сколько раз один вид образца приносит очки. */
    public static final int ANALYSIS_LIMIT = 5;

    private final Map<ScienceType, Integer> points = new EnumMap<>(ScienceType.class);
    private final Set<CelestialBody> discoveries = new LinkedHashSet<>();
    private final Set<Identifier> research = new LinkedHashSet<>();
    private final Set<String> cosmopedia = new LinkedHashSet<>();
    /** Цель телескопа → номер игровых суток, когда её снимали в последний раз. */
    private final Map<String, Long> lastObservation = new HashMap<>();
    /** Предмет → сколько раз его анализировали. */
    private final Map<Identifier, Integer> analyzed = new HashMap<>();
    /** Исторические «первые разы» программы: первая посадка на Луну и т. п. */
    private final Set<String> firsts = new LinkedHashSet<>();

    public SpaceProgram() {
        for (CelestialBody body : CelestialBody.values()) {
            if (body.knownFromStart()) {
                discoveries.add(body);
            }
        }
        for (Research node : Research.ALL) {
            if (node.isRoot()) {
                research.add(node.id());
            }
        }
        for (CosmopediaEntry entry : CosmopediaEntry.ALL) {
            if (entry.knownFromStart()) {
                cosmopedia.add(entry.id());
            }
        }
    }

    public static SpaceProgram get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(SpaceProgram::fromNbt, SpaceProgram::new, SAVE_ID);
    }

    // ------------------------------------------------------------------ чтение

    public int points(ScienceType type) {
        return points.getOrDefault(type, 0);
    }

    public boolean isDiscovered(CelestialBody body) {
        return discoveries.contains(body);
    }

    public boolean hasResearch(@Nullable Identifier id) {
        return id == null || research.contains(id);
    }

    public boolean hasEntry(String id) {
        return cosmopedia.contains(id);
    }

    public boolean observedToday(ObservationTarget target, long day) {
        return lastObservation.getOrDefault(target.id(), -1L) == day;
    }

    public int timesAnalyzed(Identifier item) {
        return analyzed.getOrDefault(item, 0);
    }

    /** Можно ли начать исследование прямо сейчас. */
    public ResearchStatus status(Research node) {
        if (research.contains(node.id())) {
            return ResearchStatus.DONE;
        }
        for (Identifier parent : node.parents()) {
            if (!research.contains(parent)) {
                return ResearchStatus.LOCKED;
            }
        }
        if (node.requiredDiscovery() != null && !isDiscovered(node.requiredDiscovery())) {
            return ResearchStatus.NEEDS_DISCOVERY;
        }
        for (ScienceType type : ScienceType.values()) {
            if (points(type) < node.cost(type)) {
                return ResearchStatus.NOT_ENOUGH_POINTS;
            }
        }
        return ResearchStatus.AVAILABLE;
    }

    // ------------------------------------------------------------------ изменения (только сервер)

    public void addPoints(MinecraftServer server, ScienceType type, int amount) {
        points.merge(type, amount, Integer::sum);
        changed(server);
    }

    /** @return true, если тело открыто впервые. */
    public boolean discover(MinecraftServer server, CelestialBody body) {
        if (!discoveries.add(body)) {
            return false;
        }
        CosmopediaEntry entry = CosmopediaEntry.byId(body.id());
        if (entry != null) {
            cosmopedia.add(entry.id());
        }
        changed(server);
        return true;
    }

    /** Засчитывает снимок цели. @return false, если цель сегодня уже снимали. */
    public boolean recordObservation(MinecraftServer server, ObservationTarget target, long day) {
        if (observedToday(target, day)) {
            return false;
        }
        lastObservation.put(target.id(), day);
        points.merge(ScienceType.OBSERVATION, target.points(), Integer::sum);
        changed(server);
        return true;
    }

    /** Засчитывает анализ образца. @return false, если этот образец уже изучен до предела. */
    public boolean recordAnalysis(MinecraftServer server, Identifier item, int samples, @Nullable String entry) {
        if (timesAnalyzed(item) >= ANALYSIS_LIMIT) {
            return false;
        }
        analyzed.merge(item, 1, Integer::sum);
        points.merge(ScienceType.SAMPLE, samples, Integer::sum);
        if (entry != null && CosmopediaEntry.byId(entry) != null) {
            cosmopedia.add(entry);
        }
        changed(server);
        return true;
    }

    /** Тратит очки и открывает исследование. @return false, если оно сейчас недоступно. */
    public boolean unlock(MinecraftServer server, Research node) {
        if (status(node) != ResearchStatus.AVAILABLE) {
            return false;
        }
        for (ScienceType type : ScienceType.values()) {
            points.merge(type, -node.cost(type), Integer::sum);
        }
        research.add(node.id());
        changed(server);
        return true;
    }

    /** Открывает статью Космопедии; true, если её ещё не было. */
    public boolean addEntry(MinecraftServer server, String id) {
        if (CosmopediaEntry.byId(id) == null || !cosmopedia.add(id)) {
            return false;
        }
        changed(server);
        return true;
    }

    /** Отмечает событие; true, если это случилось впервые за всю программу. */
    public boolean markFirst(MinecraftServer server, String event) {
        if (!firsts.add(event)) {
            return false;
        }
        changed(server);
        return true;
    }

    public boolean hasFirst(String event) {
        return firsts.contains(event);
    }

    private void changed(MinecraftServer server) {
        markDirty();
        OrbitNetworking.syncProgram(server, this);
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound pointsNbt = new NbtCompound();
        points.forEach((type, value) -> pointsNbt.putInt(type.id(), value));
        nbt.put("Points", pointsNbt);
        nbt.put("Discoveries", strings(discoveries.stream().map(CelestialBody::id).toList()));
        nbt.put("Research", strings(research.stream().map(Identifier::toString).toList()));
        nbt.put("Cosmopedia", strings(cosmopedia));
        NbtCompound observations = new NbtCompound();
        lastObservation.forEach(observations::putLong);
        nbt.put("LastObservation", observations);
        NbtCompound analyzedNbt = new NbtCompound();
        analyzed.forEach((item, count) -> analyzedNbt.putInt(item.toString(), count));
        nbt.put("Analyzed", analyzedNbt);
        nbt.put("Firsts", strings(firsts));
        return nbt;
    }

    public static SpaceProgram fromNbt(NbtCompound nbt) {
        SpaceProgram program = new SpaceProgram();
        NbtCompound pointsNbt = nbt.getCompound("Points");
        for (ScienceType type : ScienceType.values()) {
            program.points.put(type, pointsNbt.getInt(type.id()));
        }
        for (NbtElement element : nbt.getList("Discoveries", NbtElement.STRING_TYPE)) {
            CelestialBody body = CelestialBody.byId(element.asString());
            if (body != null) {
                program.discoveries.add(body);
            }
        }
        for (NbtElement element : nbt.getList("Research", NbtElement.STRING_TYPE)) {
            Identifier id = Identifier.tryParse(element.asString());
            if (id != null) {
                program.research.add(id);
            }
        }
        for (NbtElement element : nbt.getList("Cosmopedia", NbtElement.STRING_TYPE)) {
            program.cosmopedia.add(element.asString());
        }
        NbtCompound observations = nbt.getCompound("LastObservation");
        for (String key : observations.getKeys()) {
            program.lastObservation.put(key, observations.getLong(key));
        }
        NbtCompound analyzedNbt = nbt.getCompound("Analyzed");
        for (String key : analyzedNbt.getKeys()) {
            Identifier id = Identifier.tryParse(key);
            if (id != null) {
                program.analyzed.put(id, analyzedNbt.getInt(key));
            }
        }
        for (NbtElement element : nbt.getList("Firsts", NbtElement.STRING_TYPE)) {
            program.firsts.add(element.asString());
        }
        return program;
    }

    private static NbtList strings(Iterable<String> values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value));
        }
        return list;
    }

    public enum ResearchStatus {
        DONE, AVAILABLE, NOT_ENOUGH_POINTS, NEEDS_DISCOVERY, LOCKED
    }
}
