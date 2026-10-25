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
    private final Set<UUID> scared = new HashSet<>();

    public static EchoSite get(ServerWorld moon) {
        return moon.getPersistentStateManager().getOrCreate(EchoSite::fromNbt, EchoSite::new, KEY);
    }

    /** Центр объекта «Эхо» по горизонтали; высота уточняется при постройке. */
    public BlockPos echo(ServerWorld moon) {
        if (echo == null) {
            Random random = Random.create(moon.getSeed() ^ 0x4543484FL);
            float angle = random.nextFloat() * MathHelper.TAU;
            echo = new BlockPos(Math.round(MathHelper.cos(angle) * ECHO_DISTANCE), 0, Math.round(MathHelper.sin(angle) * ECHO_DISTANCE));
            markDirty();
        }
        return echo;
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
