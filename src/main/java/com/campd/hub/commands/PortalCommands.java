package com.campd.hub.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.campd.hub.commands.portal.HubPortalCommand;

public final class PortalCommands {
    private PortalCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HubPortalCommand.register(dispatcher);
        });
    }
}