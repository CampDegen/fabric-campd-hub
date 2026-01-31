package com.campd.hub;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.campd.hub.commands.PortalCommands;
import com.campd.hub.portal.PortalParticles;
import com.campd.hub.portal.PortalTeleport;

public class CampDHub implements ModInitializer {
	public static final String MOD_ID = "campdhub";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("CampD Hub Mod Initialized!");
		PortalCommands.register();
		PortalParticles.register();
		PortalTeleport.register();
	}
}