package com.campd.hub.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PortalState extends PersistentState {
    public static final String KEY = "hubportal_state";

    /** One custom color name -> RGB entry for serialization. */
    public record CustomColorEntry(String name, float[] color) {}

    public static class Portal {
        public final String id;
        public final String worldId;
        public final BlockPos pos;
        public String linkId;   // nullable; only one link per portal
        /** RGB float components 0–1 for particle color, or null for default (white). */
        public final float[] color;
        /** Particle scale (default 1.0). */
        public final float scale;

        public Portal(String id, String worldId, BlockPos pos, String linkId, float[] color, float scale) {
            this.id = id;
            this.worldId = worldId;
            this.pos = pos;
            this.linkId = linkId;
            this.color = color != null && color.length == 3 ? color : new float[]{1f, 1f, 1f};
            this.scale = scale > 0 ? scale : 1f;
        }
    }

    private static final Codec<float[]> COLOR_CODEC = Codec.FLOAT.listOf().xmap(
        list -> new float[]{
            list.size() > 0 ? list.get(0) : 1f,
            list.size() > 1 ? list.get(1) : 1f,
            list.size() > 2 ? list.get(2) : 1f
        },
        arr -> java.util.List.of(arr[0], arr[1], arr[2])
    );

    private static final Codec<Portal> PORTAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(p -> p.id),
        Codec.STRING.fieldOf("world").forGetter(p -> p.worldId),
        BlockPos.CODEC.fieldOf("pos").forGetter(p -> p.pos),
        Codec.STRING.optionalFieldOf("link").forGetter(p -> Optional.ofNullable(p.linkId)),
        COLOR_CODEC.optionalFieldOf("color").forGetter(p -> Optional.of(p.color)),
        Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(p -> p.scale)
    ).apply(instance, (id, world, pos, linkOpt, colorOpt, scale) ->
        new Portal(id, world, pos, linkOpt.orElse(null), colorOpt.orElse(new float[]{1f, 1f, 1f}), scale)));

    /** For serializing custom color names -> RGB. */
    private static final Codec<CustomColorEntry> CUSTOM_COLOR_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("name").forGetter(CustomColorEntry::name),
        COLOR_CODEC.fieldOf("color").forGetter(CustomColorEntry::color)
    ).apply(instance, CustomColorEntry::new));

    private static final Codec<PortalState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.list(PORTAL_CODEC).fieldOf("portals").forGetter(s -> new ArrayList<>(s.portals.values())),
        Codec.list(CUSTOM_COLOR_ENTRY_CODEC).optionalFieldOf("customColors", List.of()).forGetter(s ->
            s.customColors.entrySet().stream()
                .map(e -> new CustomColorEntry(e.getKey(), e.getValue()))
                .toList())
    ).apply(instance, (list, customList) -> {
        PortalState s = new PortalState();
        list.forEach(p -> s.portals.put(p.id, p));
        if (customList != null) customList.forEach(e -> s.customColors.put(e.name(), e.color()));
        return s;
    }));

    private static final net.minecraft.world.PersistentStateType<PortalState> TYPE =
        new net.minecraft.world.PersistentStateType<>(
            KEY,
            PortalState::new,
            CODEC,
            null
        );

    private final Map<String, Portal> portals = new HashMap<>();
    private final Map<String, float[]> customColors = new HashMap<>();

    public PortalState() {}

    public Map<String, Portal> getPortals() {
        return portals;
    }

    public Portal get(String id) {
        return portals.get(id);
    }

    public void put(Portal portal) {
        portals.put(portal.id, portal);
        markDirty();
    }

    public Portal remove(String id) {
        Portal removed = portals.remove(id);
        if (removed != null) markDirty();
        return removed;
    }

    /** Renames a portal; updates the linked portal's linkId if present. Returns false if portal missing or newId already exists. */
    public boolean rename(String oldId, String newId) {
        Portal p = portals.remove(oldId);
        if (p == null) return false;
        if (portals.containsKey(newId)) {
            portals.put(oldId, p);
            return false;
        }
        portals.put(newId, new Portal(newId, p.worldId, p.pos, p.linkId, p.color, p.scale));
        if (p.linkId != null) {
            Portal other = portals.get(p.linkId);
            if (other != null) other.linkId = newId;
        }
        markDirty();
        return true;
    }

    /** Replaces a portal with the same id but new color (used for edit color). */
    public boolean setColor(String id, float[] color) {
        Portal p = portals.get(id);
        if (p == null) return false;
        portals.put(p.id, new Portal(p.id, p.worldId, p.pos, p.linkId, color != null && color.length == 3 ? color : new float[]{1f, 1f, 1f}, p.scale));
        markDirty();
        return true;
    }

    /** Replaces a portal with the same id but new particle scale. */
    public boolean setScale(String id, float scale) {
        Portal p = portals.get(id);
        if (p == null) return false;
        portals.put(p.id, new Portal(p.id, p.worldId, p.pos, p.linkId, p.color, scale > 0 ? scale : 1f));
        markDirty();
        return true;
    }

    public void linkBoth(String a, String b) {
        Portal pa = portals.get(a);
        Portal pb = portals.get(b);
        if (pa == null || pb == null) return;
        pa.linkId = b;
        pb.linkId = a;
        markDirty();
    }

    public void unlinkBoth(String a, String b) {
        Portal pa = portals.get(a);
        Portal pb = portals.get(b);
        if (pa != null && b.equals(pa.linkId)) pa.linkId = null;
        if (pb != null && a.equals(pb.linkId)) pb.linkId = null;
        markDirty();
    }

    /** Custom color names -> RGB (0–1). Does not include Minecraft dye names. */
    public Map<String, float[]> getCustomColors() {
        return java.util.Collections.unmodifiableMap(customColors);
    }

    public void putCustomColor(String name, float[] rgb) {
        if (name == null || rgb == null || rgb.length != 3) return;
        customColors.put(name, new float[]{rgb[0], rgb[1], rgb[2]});
        markDirty();
    }

    /** Gets global portal state from the overworld (all portals on the server). */
    public static PortalState get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return new PortalState();
        return overworld.getPersistentStateManager().getOrCreate(TYPE);
    }

    /** Convenience: get state from a world's server. */
    public static PortalState get(ServerWorld world) {
        return get(world.getServer());
    }
}
