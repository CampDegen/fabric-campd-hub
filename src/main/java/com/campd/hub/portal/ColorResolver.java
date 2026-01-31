package com.campd.hub.portal;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DyeColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves color names (Minecraft dye, custom, or "r,g,b") to RGB and parses
 * combined color/scale strings for portal create. Used by commands and suggestions.
 */
public final class ColorResolver {
    /** Default color name when none specified. */
    public static final String DEFAULT_COLOR = "white";
    /** Default particle scale. */
    public static final float DEFAULT_SCALE = 1.0f;

    public static final String[] SCALE_SUGGESTIONS = {"0.5", "1.0", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0"};

    /** Result of parsing create's optional color|scale argument. */
    public record ColorAndScale(String colorStr, float scale) {}

    private ColorResolver() {}

    /** Normalize color name for lookup: lowercase, spaces to underscores. */
    public static String normalizeColorName(String name) {
        return name == null ? "" : name.trim().toLowerCase().replace(' ', '_');
    }

    /**
     * Parse "color [scale]" from create: color is name or r,g,b; optional last token
     * as scale (0.1–10). Single number 0.1–10 is scale only (default color).
     */
    public static ColorAndScale parseColorAndScale(String colorAndScale) {
        if (colorAndScale == null || colorAndScale.isEmpty())
            return new ColorAndScale(DEFAULT_COLOR, DEFAULT_SCALE);
        String trimmed = colorAndScale.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            try {
                float scale = Float.parseFloat(parts[0]);
                if (scale >= 0.1f && scale <= 10f)
                    return new ColorAndScale(DEFAULT_COLOR, scale);
            } catch (NumberFormatException ignored) {}
            return new ColorAndScale(trimmed, DEFAULT_SCALE);
        }
        try {
            float last = Float.parseFloat(parts[parts.length - 1]);
            if (last >= 0.1f && last <= 10f) {
                String colorStr = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1)).trim();
                if (!colorStr.isEmpty())
                    return new ColorAndScale(colorStr, last);
            }
        } catch (NumberFormatException ignored) {}
        try {
            float first = Float.parseFloat(parts[0]);
            if (first >= 0.1f && first <= 10f) {
                String colorStr = parts.length == 2 ? parts[1] : String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
                if (!colorStr.isEmpty())
                    return new ColorAndScale(colorStr.trim(), first);
            }
        } catch (NumberFormatException ignored) {}
        return new ColorAndScale(trimmed, DEFAULT_SCALE);
    }

    /**
     * Resolve color to RGB (0–1). Tries Minecraft dye by name, then custom colors,
     * then "r,g,b" (0–1). Returns white if server is null or parsing fails.
     */
    public static float[] parseColor(MinecraftServer server, String colorStr) {
        if (colorStr == null || colorStr.isEmpty())
            return new float[]{1f, 1f, 1f};
        String normalized = normalizeColorName(colorStr);
        DyeColor dye = DyeColor.byId(normalized, null);
        if (dye != null) {
            int rgb = dye.getSignColor();
            return new float[]{
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
            };
        }
        if (server != null) {
            var custom = PortalState.get(server).getCustomColors().get(normalized);
            if (custom != null) return new float[]{custom[0], custom[1], custom[2]};
        }
        String[] parts = colorStr.split(",");
        if (parts.length == 3) {
            try {
                return new float[]{
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim()),
                    Float.parseFloat(parts[2].trim())
                };
            } catch (NumberFormatException ignored) {}
        }
        return new float[]{1f, 1f, 1f};
    }

    /** All color names for suggestions: Minecraft dye ids plus custom color names. */
    public static List<String> getColorNames(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (DyeColor d : DyeColor.values())
            out.add(d.asString());
        if (server != null) {
            for (String name : PortalState.get(server).getCustomColors().keySet())
                out.add(name);
        }
        return out;
    }
}
