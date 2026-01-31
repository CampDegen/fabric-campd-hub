package com.campd.hub.portal;

import com.campd.hub.commands.portal.PortalState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Spawns dust particles at each portal position using the portal's color.
 * Runs every few ticks so particles are visible without being too heavy.
 */
public final class PortalParticles {
    private static final int INTERVAL_TICKS = 3;
    private static final int PARTICLE_COUNT = 60;
    private static final double OFFSET_X = 0.4;
    private static final double OFFSET_Y = 0.6;
    private static final double OFFSET_Z = 0.4;
    private static final double SPEED = 0.25;

    private PortalParticles() {}

    /** Convert portal RGB (0–1) to DustParticleEffect color int (0xRRGGBB). */
    private static int rgbToInt(float[] rgb) {
        int r = (int) (Math.clamp(rgb[0], 0, 1) * 255);
        int g = (int) (Math.clamp(rgb[1], 0, 1) * 255);
        int b = (int) (Math.clamp(rgb[2], 0, 1) * 255);
        return (r << 16) | (g << 8) | b;
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!(world instanceof ServerWorld serverWorld)) return;
            if (serverWorld.getServer().getTicks() % INTERVAL_TICKS != 0) return;

            PortalState state = PortalState.get(serverWorld.getServer());
            String worldId = serverWorld.getRegistryKey().getValue().toString();

            for (PortalState.Portal portal : state.getPortals().values()) {
                if (!portal.worldId.equals(worldId)) continue;

                BlockPos pos = portal.pos;
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 1;
                double z = pos.getZ() + 0.5;

                DustParticleEffect effect = new DustParticleEffect(rgbToInt(portal.color), portal.scale);

                serverWorld.spawnParticles(
                    effect,
                    true,   // force (show to all in range)
                    false,  // important
                    x, y, z,
                    PARTICLE_COUNT,
                    OFFSET_X, OFFSET_Y, OFFSET_Z,
                    SPEED
                );
            }
        });
    }
}
