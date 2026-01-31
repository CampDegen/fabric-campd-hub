package com.campd.hub.commands.portal;

import com.campd.hub.portal.ColorResolver;
import com.campd.hub.portal.PortalState;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;

import java.util.concurrent.CompletableFuture;

/**
 * Brigadier suggestion providers for /hubportal arguments: portal names,
 * and color/scale combined suggestions for create.
 */
public final class HubPortalSuggestions {
    private HubPortalSuggestions() {}

    /** Suggests existing portal names from the server state. */
    public static SuggestionProvider<ServerCommandSource> suggestPortalNames() {
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

    /**
     * Suggests dye names, custom color names, and scale values. After first token
     * (e.g. "red " or "1.5 ") suggests the other (scale or color).
     */
    public static SuggestionProvider<ServerCommandSource> createColorOrScaleSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            int lastSpace = remaining.lastIndexOf(' ');
            String prefix;
            String currentToken;
            boolean hasFirstToken;
            if (lastSpace >= 0) {
                prefix = remaining.substring(0, lastSpace + 1);
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
                for (String id : ColorResolver.getColorNames(context.getSource().getServer())) {
                    if (id.toLowerCase().startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(prefix + id);
                }
            } else if (hasFirstToken) {
                for (String scale : ColorResolver.SCALE_SUGGESTIONS) {
                    if (scale.startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(prefix + scale);
                }
            } else {
                for (String id : ColorResolver.getColorNames(context.getSource().getServer())) {
                    if (id.toLowerCase().startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(id);
                }
                for (String scale : ColorResolver.SCALE_SUGGESTIONS) {
                    if (scale.startsWith(currentToken) || currentToken.isEmpty())
                        builder.suggest(scale);
                }
            }
            return CompletableFuture.completedFuture(builder.build());
        };
    }
}
