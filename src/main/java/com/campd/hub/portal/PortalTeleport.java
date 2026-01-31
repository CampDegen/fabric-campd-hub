package com.campd.hub.portal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When a player stands on or in a linked portal's block, teleports them to the
 * linked portal. Trigger: feet on portal block (portal.pos) or in the block
 * above it (where particles render). Same-dimension only; cooldown prevents
 * immediate bounce-back.
 */
public final class PortalTeleport {
    /** Ticks to wait before the same player can teleport again (1.5 sec). */
    private static final int COOLDOWN_TICKS = 30;

    /** True if the player is on the portal block or in the block above it. */
    private static boolean isInPortal(BlockPos playerBlock, BlockPos portalPos) {
        return playerBlock.equals(portalPos) || playerBlock.equals(portalPos.up());
    }

    /** Destination: center of the portal block, feet on the ground. */
    private static double destX(BlockPos pos) { return pos.getX() + 0.5; }
    /**
     * Y so the player stands on the ground. Don't add 1: portal.pos is the block the
     * player was in when creating (often the air block at feet level); use it directly
     * so feet land on the top of the block below (the actual ground).
     */
    private static double destY(BlockPos pos) { return pos.getY(); }
    private static double destZ(BlockPos pos) { return pos.getZ() + 0.5; }

    private static final Map<UUID, Integer> lastTeleportTick = new HashMap<>();

    private PortalTeleport() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!(world instanceof ServerWorld serverWorld)) return;

            PortalState state = PortalState.get(serverWorld.getServer());
            String worldId = serverWorld.getRegistryKey().getValue().toString();
            int currentTick = (int) serverWorld.getTime();

            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                BlockPos playerBlock = player.getBlockPos();
                int lastTick = lastTeleportTick.getOrDefault(player.getUuid(), 0);
                if (currentTick - lastTick < COOLDOWN_TICKS) continue;

                for (PortalState.Portal portal : state.getPortals().values()) {
                    if (!portal.worldId.equals(worldId) || portal.linkId == null) continue;

                    if (!isInPortal(playerBlock, portal.pos)) continue;

                    PortalState.Portal linkPortal = state.get(portal.linkId);
                    if (linkPortal == null) continue;
                    if (!linkPortal.worldId.equals(worldId)) continue; // same-dimension only

                    double x = destX(linkPortal.pos);
                    double y = destY(linkPortal.pos);
                    double z = destZ(linkPortal.pos);

                    player.requestTeleport(x, y, z);
                    // Use null as source so the teleporting player hears it too (World.playSound excludes the source entity)
                    serverWorld.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    lastTeleportTick.put(player.getUuid(), currentTick);
                    break; // one teleport per player per tick
                }
            }
        });
    }
}
