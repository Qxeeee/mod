package com.orbitcore.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Где на Луне стоят «Заря» и объект «Эхо», что из этого уже построено и кто
 * из игроков касался Эха. Хранится в мире Луны.
 */
public class EchoSite extends PersistentState {
    /** Эхо стоит в паре тысяч блоков от центра Луны, направление зависит от сида мира. */
    public static final int ECHO_DISTANCE = 2000;
    private static final String KEY = "orbitcore_echo";

    @Nullable
    private BlockPos echo;
    @Nullable
    private BlockPos zarya;
    private boolean echoBuilt;
    /** Через сколько тиков придёт сигнал; −1 — отсчёт не запущен. */
    private int signalCountdown = -1;
    private final Set<UUID> contacted = new HashSet<>();
    /** Планшеты сюжета («Заря», кратер Эха): запись → где лежит. */
    private final Map<String, BlockPos> tablets = new LinkedHashMap<>();
    /** Планшеты, чей сигнал уже иссяк: записи сюжета и клетки руин. */
    private final Set<String> drained = new HashSet<>();
    private final Set<UUID> scared = new HashSet<>();

    public static EchoSite get(ServerWorld moon) {
        return moon.getPersistentStateManager().getOrCreate(EchoSite::fromNbt, EchoSite::new, KEY);
    }

    /** Центр объекта «Эхо» по горизонтали; высота уточняется при постройке. */
    public BlockPos echo(ServerWorld moon) {
        if (echo == null) {
            echo = echoColumn(moon.getSeed());
            markDirty();
        }
        return echo;
    }

    /** Где по сиду стоит Эхо (y = 0). Нужен и генератору руин, которому сам мир Луны недоступен. */
    public static BlockPos echoColumn(long seed) {
        Random random = Random.create(seed ^ 0x4543484FL);
        float angle = random.nextFloat() * MathHelper.TAU;
        return new BlockPos(Math.round(MathHelper.cos(angle) * ECHO_DISTANCE), 0, Math.round(MathHelper.sin(angle) * ECHO_DISTANCE));
    }

    public void addTablet(String log, BlockPos pos) {
        tablets.put(log, pos.toImmutable());
        markDirty();
    }

    public Map<String, BlockPos> tablets() {
        return tablets;
    }

    public boolean drained(String key) {
        return drained.contains(key);
    }

    public void drain(String key) {
        if (drained.add(key)) {
            markDirty();
        }
    }

    public void setEcho(BlockPos pos) {
        echo = pos;
        echoBuilt = true;
        markDirty();
    }

    public boolean echoBuilt() {
        return echoBuilt;
    }

    @Nullable
    public BlockPos zarya() {
        return zarya;
    }

    public void setZarya(BlockPos pos) {
        zarya = pos;
        markDirty();
    }

    public int signalCountdown() {
        return signalCountdown;
    }

    public void setSignalCountdown(int ticks) {
        signalCountdown = ticks;
        markDirty();
    }

    /** Отмечает касание Эха; true, если этот игрок коснулся его впервые. */
    public boolean markContact(UUID player) {
        boolean added = contacted.add(player);
        if (added) {
            markDirty();
        }
        return added;
    }

    /** Отмечает «страшный момент»; true, если с этим игроком он ещё не случался. */
    public boolean markScared(UUID player) {
        boolean added = scared.add(player);
        if (added) {
            markDirty();
        }
        return added;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (echo != null) {
            nbt.put("Echo", NbtHelper.fromBlockPos(echo));
        }
        if (zarya != null) {
            nbt.put("Zarya", NbtHelper.fromBlockPos(zarya));
        }
        nbt.putBoolean("EchoBuilt", echoBuilt);
        nbt.putInt("SignalCountdown", signalCountdown);
        nbt.put("Contacted", uuids(contacted));
        nbt.put("Scared", uuids(scared));
        NbtCompound tabletsNbt = new NbtCompound();
        tablets.forEach((log, pos) -> tabletsNbt.put(log, NbtHelper.fromBlockPos(pos)));
        nbt.put("Tablets", tabletsNbt);
        NbtList drainedNbt = new NbtList();
        drained.forEach(key -> drainedNbt.add(NbtString.of(key)));
        nbt.put("Drained", drainedNbt);
        return nbt;
    }

    private static EchoSite fromNbt(NbtCompound nbt) {
        EchoSite site = new EchoSite();
        if (nbt.contains("Echo")) {
            site.echo = NbtHelper.toBlockPos(nbt.getCompound("Echo"));
        }
        if (nbt.contains("Zarya")) {
            site.zarya = NbtHelper.toBlockPos(nbt.getCompound("Zarya"));
        }
        site.echoBuilt = nbt.getBoolean("EchoBuilt");
        site.signalCountdown = nbt.contains("SignalCountdown") ? nbt.getInt("SignalCountdown") : -1;
        readUuids(nbt.getList("Contacted", NbtElement.STRING_TYPE), site.contacted);
        readUuids(nbt.getList("Scared", NbtElement.STRING_TYPE), site.scared);
        NbtCompound tabletsNbt = nbt.getCompound("Tablets");
        for (String log : tabletsNbt.getKeys()) {
            site.tablets.put(log, NbtHelper.toBlockPos(tabletsNbt.getCompound(log)));
        }
        NbtList drainedNbt = nbt.getList("Drained", NbtElement.STRING_TYPE);
        for (int i = 0; i < drainedNbt.size(); i++) {
            site.drained.add(drainedNbt.getString(i));
        }
        return site;
    }

    private static NbtList uuids(Set<UUID> set) {
        NbtList list = new NbtList();
        for (UUID uuid : set) {
            list.add(NbtString.of(uuid.toString()));
        }
        return list;
    }

    private static void readUuids(NbtList list, Set<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            into.add(UUID.fromString(list.getString(i)));
        }
    }
}
