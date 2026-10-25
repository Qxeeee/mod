package com.orbitcore.science;

import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModBlocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Серверная логика ЦУП: изучение узлов дерева исследований.
 */
public final class MissionControl {
    private static final double MAX_DISTANCE_SQ = 8 * 8;

    private MissionControl() {
    }

    public static void unlock(ServerPlayerEntity player, BlockPos pos, Identifier researchId) {
        Research node = Research.byId(researchId);
        if (node == null
                || !player.getServerWorld().getBlockState(pos).isOf(ModBlocks.MISSION_CONTROL)
                || player.squaredDistanceTo(pos.toCenterPos()) > MAX_DISTANCE_SQ) {
            return;
        }
        var server = player.getServerWorld().getServer();
        if (SpaceProgram.get(server).unlock(server, node)) {
            server.getPlayerManager().broadcast(Text.translatable("message.orbitcore.research_done",
                    player.getDisplayName(), Text.translatable(node.translationKey())).formatted(Formatting.AQUA), false);
            OrbitNetworking.announceResearch(server, node);
        }
    }
}
