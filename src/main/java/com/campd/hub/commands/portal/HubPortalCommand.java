package com.campd.hub.commands.portal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class HubPortalCommand {
    private HubPortalCommand() {}

    /** Normalize color name for lookup: lowercase, spaces to underscores. */
    private static String normalizeColorName(String name) {
        return name == null ? "" : name.trim().toLowerCase().replace(' ', '_');
    }

    /** Result of parsing create's color argument: may include optional scale. */
    private static final class ColorAndScale {
        final String colorStr;
        final float scale;

        ColorAndScale(String colorStr, float scale) {
            this.colorStr = colorStr;
            this.scale = scale;
        }
    }

    /** Parse "color [scale]" from create: color is name or r,g,b; optional last token as scale (0.1–10). */
    private static ColorAndScale parseColorAndScale(String colorAndScale) {
        if (colorAndScale == null || colorAndScale.isEmpty())
            return new ColorAndScale("white", 1.0f);
        String trimmed = colorAndScale.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            try {
                float scale = Float.parseFloat(parts[0]);
                if (scale >= 0.1f && scale <= 10f)
                    return new ColorAndScale("white", scale);  // scale only, default color
            } catch (NumberFormatException ignored) {}
            return new ColorAndScale(trimmed, 1.0f);
        }
        // 2+ tokens: try "color scale" (last token = scale), then "scale color" (first token = scale)
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
        return new ColorAndScale(trimmed, 1.0f);
    }

    /** Parse color from Minecraft dye name, custom color name, or "r,g,b" (0–1). Requires source for custom colors. */
    private static float[] parseColor(ServerCommandSource src, String colorStr) {
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
        if (src != null) {
            var custom = PortalState.get(src.getServer()).getCustomColors().get(normalized);
            if (custom != null) return new float[]{custom[0], custom[1], custom[2]};
        }
        // Try "r,g,b" (0–1 floats)
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

    private static final String[] SCALE_SUGGESTIONS = {"0.5", "1.0", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0"};

    /** Suggests dye names, custom color names, and scale values. After first token (e.g. "red " or "1.5 ") suggests the other (scale or color). */
    private static SuggestionProvider<ServerCommandSource> createColorOrScaleSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            int lastSpace = remaining.lastIndexOf(' ');
            String prefix;
            String currentToken;
            boolean hasFirstToken;
            if (lastSpace >= 0) {
                prefix = remaining.substring(0, lastSpace + 1);  // e.g. "red " or "1.5 "
                currentToken = remaining.substring(lastSpace + 1).toLowerCase();
                hasFirstToken = true;
            } else {
                prefix = "";
                currentToken = remaining.toLowerCase();
                hasFirstToken = false;
            }
            boolean firstIsScale = false;
            if (hasFirstToken) {
                String first = prefix.trim();
                try {
                    float f = Float.parseFloat(first);
                    firstIsScale = f >= 0.1f && f <= 10f;
                } catch (NumberFormatException ignored) {}
            }
            if (hasFirstToken && firstIsScale) {
                // First token is scale → suggest colors for second token
                for (DyeColor d : DyeColor.values()) {
                    String id = d.asString();
                    if (id.startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(prefix + id);
                }
                try {
                    var state = PortalState.get(context.getSource().getServer()).getCustomColors();
                    for (String name : state.keySet()) {
                        if (name.toLowerCase().startsWith(currentToken) || currentToken.isEmpty())
                            builder.suggest(prefix + name);
                    }
                } catch (Exception ignored) {}
            } else if (hasFirstToken) {
                // First token is color → suggest scale values for second token
                for (String scale : SCALE_SUGGESTIONS) {
                    if (scale.startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(prefix + scale);
                }
            } else {
                // No first token yet → suggest colors and scales
                for (DyeColor d : DyeColor.values()) {
                    String id = d.asString();
                    if (id.startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(id);
                }
                try {
                    var state = PortalState.get(context.getSource().getServer()).getCustomColors();
                    for (String name : state.keySet()) {
                        if (name.toLowerCase().startsWith(currentToken) || currentToken.isEmpty())
                            builder.suggest(name);
                    }
                } catch (Exception ignored) {}
                for (String scale : SCALE_SUGGESTIONS) {
                    if (scale.startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(scale);
                }
            }
            return CompletableFuture.completedFuture(builder.build());
        };
    }

    /** Suggests existing portal names from the server state. */
    private static SuggestionProvider<ServerCommandSource> suggestPortalNames() {
        return (context, builder) -> {
            String remaining = builder.getRemaining().toLowerCase();
            try {
                var portals = PortalState.get(context.getSource().getServer()).getPortals().keySet();
                for (String name : portals) {
                    if (name.toLowerCase().startsWith(remaining) || remaining.isEmpty())
                        builder.suggest(name);
                }
            } catch (Exception ignored) {}
            return CompletableFuture.completedFuture(builder.build());
        };
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("hubportal")
                .requires(src -> {
                    if (!src.isExecutedByPlayer()) return false;
                    ServerPlayerEntity player = src.getPlayer();
                    return player != null && src.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
                })
                .then(literal("create")
                    .then(argument("name", StringArgumentType.word())
                        .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "white"))
                        .then(argument("color|scale", StringArgumentType.greedyString())
                            .suggests(createColorOrScaleSuggestions())
                            .executes(ctx -> create(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "color|scale"))))
                    )
                )
                .then(literal("link")
                    .then(argument("name1", StringArgumentType.word())
                        .suggests(suggestPortalNames())
                        .then(argument("name2", StringArgumentType.word())
                            .suggests(suggestPortalNames())
                            .executes(ctx -> link(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name1"),
                                StringArgumentType.getString(ctx, "name2")
                            ))
                        )
                    )
                )
                .then(literal("unlink")
                    .then(argument("name1", StringArgumentType.word())
                        .suggests(suggestPortalNames())
                        .then(argument("name2", StringArgumentType.word())
                            .suggests(suggestPortalNames())
                            .executes(ctx -> unlink(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name1"),
                                StringArgumentType.getString(ctx, "name2")
                            ))
                        )
                    )
                )
                .then(literal("delete")
                    .then(argument("name", StringArgumentType.word())
                        .suggests(suggestPortalNames())
                        .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
                .then(literal("list")
                    .then(literal("portals")
                        .executes(ctx -> listPortals(ctx.getSource()))
                    )
                    .then(literal("links")
                        .executes(ctx -> listLinks(ctx.getSource()))
                    )
                )
                .then(literal("info")
                    .then(argument("name", StringArgumentType.word())
                        .suggests(suggestPortalNames())
                        .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
                .then(literal("edit")
                    .then(argument("name", StringArgumentType.word())
                        .suggests(suggestPortalNames())
                        .then(literal("name")
                            .then(argument("newName", StringArgumentType.word())
                                .executes(ctx -> editName(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "newName")))
                                .then(literal("color")
                                    .then(argument("color", StringArgumentType.greedyString())
                                        .executes(ctx -> editNameAndColor(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "name"),
                                            StringArgumentType.getString(ctx, "newName"),
                                            StringArgumentType.getString(ctx, "color"))))
                                )
                            )
                        )
                        .then(literal("color")
                            .then(argument("color", StringArgumentType.greedyString())
                                .executes(ctx -> editColor(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "color")))
                                .then(literal("name")
                                    .then(argument("newName", StringArgumentType.word())
                                        .executes(ctx -> editNameAndColor(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "name"),
                                            StringArgumentType.getString(ctx, "newName"),
                                            StringArgumentType.getString(ctx, "color"))))
                                )
                            )
                        )
                        .then(literal("scale")
                            .then(argument("scale", FloatArgumentType.floatArg(0.1f, 10f))
                                .executes(ctx -> editScale(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    FloatArgumentType.getFloat(ctx, "scale"))))
                        )
                    )
                )
                .then(literal("color")
                    .then(literal("add")
                        .then(argument("name", StringArgumentType.word())
                            .then(argument("color", StringArgumentType.greedyString())
                                .executes(ctx -> colorAdd(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "color")))))
                    )
                    .then(literal("edit")
                        .then(argument("name", StringArgumentType.word())
                            .then(argument("color", StringArgumentType.greedyString())
                                .executes(ctx -> colorEdit(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "color")))))
                    )
                )
        );
    }

    private static int create(ServerCommandSource src, String name, String colorAndScaleStr) throws CommandSyntaxException {
        ServerPlayerEntity player = src.getPlayerOrThrow();
        ServerWorld world = src.getWorld();
        PortalState state = PortalState.get(src.getServer());

        if (state.get(name) != null) {
            src.sendError(Text.literal("Portal '" + name + "' already exists."));
            return 0;
        }

        ColorAndScale parsed = parseColorAndScale(colorAndScaleStr);
        float[] color = parseColor(src, parsed.colorStr);

        BlockPos pos = player.getBlockPos();
        String worldId = world.getRegistryKey().getValue().toString();
        state.put(new PortalState.Portal(name, worldId, pos, null, color, parsed.scale));

        String scaleStr = parsed.scale == 1.0f ? "" : ", scale: " + parsed.scale;
        src.sendFeedback(() -> Text.literal("Created portal '" + name + "' at " +
            pos.getX() + " " + pos.getY() + " " + pos.getZ() + " in " + worldId + " (color: " + parsed.colorStr + scaleStr + ")"), false);
        return 1;
    }

    private static int link(ServerCommandSource src, String a, String b) {
        if (a.equals(b)) {
            src.sendError(Text.literal("Cannot link a portal to itself."));
            return 0;
        }

        PortalState state = PortalState.get(src.getServer());
        PortalState.Portal pa = state.get(a);
        PortalState.Portal pb = state.get(b);

        if (pa == null) {
            src.sendError(Text.literal("Portal '" + a + "' does not exist."));
            return 0;
        }
        if (pb == null) {
            src.sendError(Text.literal("Portal '" + b + "' does not exist."));
            return 0;
        }
        if (pa.linkId != null) {
            src.sendError(Text.literal("Portal '" + a + "' is already linked to '" + pa.linkId + "'. Unlink it first."));
            return 0;
        }
        if (pb.linkId != null) {
            src.sendError(Text.literal("Portal '" + b + "' is already linked to '" + pb.linkId + "'. Unlink it first."));
            return 0;
        }

        state.linkBoth(a, b);
        src.sendFeedback(() -> Text.literal("Linked '" + a + "' <-> '" + b + "'."), false);
        return 1;
    }

    private static int unlink(ServerCommandSource src, String a, String b) {
        PortalState state = PortalState.get(src.getServer());
        PortalState.Portal pa = state.get(a);
        PortalState.Portal pb = state.get(b);

        if (pa == null) {
            src.sendError(Text.literal("Portal '" + a + "' does not exist."));
            return 0;
        }
        if (pb == null) {
            src.sendError(Text.literal("Portal '" + b + "' does not exist."));
            return 0;
        }
        if (!b.equals(pa.linkId) || !a.equals(pb.linkId)) {
            src.sendError(Text.literal("Portals '" + a + "' and '" + b + "' are not linked."));
            return 0;
        }

        state.unlinkBoth(a, b);
        src.sendFeedback(() -> Text.literal("Unlinked '" + a + "' and '" + b + "'."), false);
        return 1;
    }

    private static int delete(ServerCommandSource src, String name) {
        PortalState state = PortalState.get(src.getServer());
        PortalState.Portal portal = state.get(name);

        if (portal == null) {
            src.sendError(Text.literal("Portal '" + name + "' does not exist."));
            return 0;
        }
        if (portal.linkId != null) {
            src.sendError(Text.literal("Portal '" + name + "' is linked to '" + portal.linkId + "'. Unlink first with: /hubportal unlink " + name + " " + portal.linkId));
            return 0;
        }

        state.remove(name);
        src.sendFeedback(() -> Text.literal("Deleted portal '" + name + "'."), false);
        return 1;
    }

    private static int listPortals(ServerCommandSource src) {
        PortalState state = PortalState.get(src.getServer());
        if (state.getPortals().isEmpty()) {
            src.sendFeedback(() -> Text.literal("No portals."), false);
            return 0;
        }
        state.getPortals().values().forEach(p -> {
            String linkStr = p.linkId != null ? " -> " + p.linkId : "";
            src.sendFeedback(() -> Text.literal("  " + p.id + " @ " + p.pos.getX() + " " + p.pos.getY() + " " + p.pos.getZ() + " (" + p.worldId + ")" + linkStr), false);
        });
        return state.getPortals().size();
    }

    private static int listLinks(ServerCommandSource src) {
        PortalState state = PortalState.get(src.getServer());
        int count = 0;
        var seen = new java.util.HashSet<String>();
        for (PortalState.Portal p : state.getPortals().values()) {
            if (p.linkId != null && !seen.contains(p.linkId)) {
                seen.add(p.id);
                src.sendFeedback(() -> Text.literal("  " + p.id + " <-> " + p.linkId), false);
                count++;
            }
        }
        if (count == 0)
            src.sendFeedback(() -> Text.literal("No links."), false);
        return count;
    }

    private static int info(ServerCommandSource src, String name) {
        PortalState state = PortalState.get(src.getServer());
        PortalState.Portal p = state.get(name);
        if (p == null) {
            src.sendError(Text.literal("Portal '" + name + "' does not exist."));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Portal: " + p.id).formatted(Formatting.GOLD), false);
        src.sendFeedback(() -> Text.literal("  Position: " + p.pos.getX() + " " + p.pos.getY() + " " + p.pos.getZ()), false);
        src.sendFeedback(() -> Text.literal("  Dimension: " + p.worldId), false);
        src.sendFeedback(() -> Text.literal("  Linked to: " + (p.linkId != null ? p.linkId : "none")), false);
        src.sendFeedback(() -> Text.literal("  Color: " + String.format("%.2f, %.2f, %.2f", p.color[0], p.color[1], p.color[2])), false);
        src.sendFeedback(() -> Text.literal("  Scale: " + p.scale), false);
        return 1;
    }

    private static int editScale(ServerCommandSource src, String name, float scale) {
        PortalState state = PortalState.get(src.getServer());
        if (state.get(name) == null) {
            src.sendError(Text.literal("Portal '" + name + "' does not exist."));
            return 0;
        }
        if (!state.setScale(name, scale)) {
            src.sendError(Text.literal("Could not update scale."));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Updated portal '" + name + "' particle scale to " + scale + "."), false);
        return 1;
    }

    private static int editName(ServerCommandSource src, String name, String newName) {
        if (name.equals(newName)) {
            src.sendError(Text.literal("New name is the same as current name."));
            return 0;
        }
        PortalState state = PortalState.get(src.getServer());
        if (state.get(name) == null) {
            src.sendError(Text.literal("Portal '" + name + "' does not exist."));
            return 0;
        }
        if (state.get(newName) != null) {
            src.sendError(Text.literal("Portal '" + newName + "' already exists. Choose a different name."));
            return 0;
        }
        if (!state.rename(name, newName)) {
            src.sendError(Text.literal("Could not rename portal."));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Renamed portal '" + name + "' to '" + newName + "'."), false);
        return 1;
    }

    private static int editColor(ServerCommandSource src, String name, String colorStr) {
        PortalState state = PortalState.get(src.getServer());
        if (state.get(name) == null) {
            src.sendError(Text.literal("Portal '" + name + "' does not exist."));
            return 0;
        }
        float[] color = parseColor(src, colorStr);
        if (!state.setColor(name, color)) {
            src.sendError(Text.literal("Could not update color."));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Updated portal '" + name + "' color to " + colorStr + "."), false);
        return 1;
    }

    private static int editNameAndColor(ServerCommandSource src, String name, String newName, String colorStr) {
        if (name.equals(newName)) {
            src.sendError(Text.literal("New name is the same as current name."));
            return 0;
        }
        PortalState state = PortalState.get(src.getServer());
        if (state.get(name) == null) {
            src.sendError(Text.literal("Portal '" + name + "' does not exist."));
            return 0;
        }
        if (state.get(newName) != null) {
            src.sendError(Text.literal("Portal '" + newName + "' already exists. Choose a different name."));
            return 0;
        }
        float[] color = parseColor(src, colorStr);
        if (!state.rename(name, newName)) {
            src.sendError(Text.literal("Could not rename portal."));
            return 0;
        }
        state.setColor(newName, color);
        src.sendFeedback(() -> Text.literal("Renamed portal '" + name + "' to '" + newName + "' and set color to " + colorStr + "."), false);
        return 1;
    }

    private static int colorAdd(ServerCommandSource src, String name, String colorValueStr) {
        String normalized = normalizeColorName(name);
        if (normalized.isEmpty()) {
            src.sendError(Text.literal("Color name cannot be empty."));
            return 0;
        }
        if (DyeColor.byId(normalized, null) != null) {
            src.sendError(Text.literal("'" + normalized + "' is a Minecraft dye color and cannot be overridden. Choose a different name."));
            return 0;
        }
        PortalState state = PortalState.get(src.getServer());
        if (state.getCustomColors().containsKey(normalized)) {
            src.sendError(Text.literal("A custom color named '" + normalized + "' already exists. Use '/hubportal color edit " + normalized + " <color>' to change it."));
            return 0;
        }
        float[] rgb = parseColor(src, colorValueStr);
        state.putCustomColor(normalized, rgb);
        src.sendFeedback(() -> Text.literal("Added custom color '" + normalized + "' (RGB " + String.format("%.2f, %.2f, %.2f", rgb[0], rgb[1], rgb[2]) + ")."), false);
        return 1;
    }

    private static int colorEdit(ServerCommandSource src, String name, String colorValueStr) {
        String normalized = normalizeColorName(name);
        if (normalized.isEmpty()) {
            src.sendError(Text.literal("Color name cannot be empty."));
            return 0;
        }
        if (DyeColor.byId(normalized, null) != null) {
            src.sendError(Text.literal("You cannot edit Minecraft dye colors. Use a custom color name."));
            return 0;
        }
        PortalState state = PortalState.get(src.getServer());
        if (!state.getCustomColors().containsKey(normalized)) {
            src.sendError(Text.literal("No custom color named '" + normalized + "'. Use '/hubportal color add " + normalized + " <color>' to create one."));
            return 0;
        }
        float[] rgb = parseColor(src, colorValueStr);
        state.putCustomColor(normalized, rgb);
        src.sendFeedback(() -> Text.literal("Updated custom color '" + normalized + "' (RGB " + String.format("%.2f, %.2f, %.2f", rgb[0], rgb[1], rgb[2]) + ")."), false);
        return 1;
    }
}
