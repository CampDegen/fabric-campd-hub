package com.campd.hub.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class HelloCommand {
    private HelloCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("hello")
                        .then(argument("name", word())
                                .executes(ctx -> {
                                    String name = getString(ctx, "name");
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("Hello, " + name + "!"),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("Hello!"), false);
                            return 1;
                        })
        );
    }
}