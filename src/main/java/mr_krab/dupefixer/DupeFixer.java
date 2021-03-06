/*
 * DupeFixer - Plugin for fix dupes on Sponge servers.
 * Copyright (C) 2019 Mr_Krab
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DupeFixer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 package mr_krab.dupefixer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;

import com.google.inject.Inject;

import br.net.fabiozumbi12.RedProtect.Sponge.RedProtect;
import me.ryanhamshire.griefprevention.GriefPrevention;
import mr_krab.dupefixer.listeners.DropListener;
import mr_krab.dupefixer.listeners.InteractItemListenerFluidFixGP;
import mr_krab.dupefixer.listeners.InteractItemListenerFluidFixRP;
import mr_krab.dupefixer.listeners.PortalListener;
import mr_krab.dupefixer.listeners.ShiftClickListener;
import mr_krab.dupefixer.utils.ConfigUtil;
import mr_krab.dupefixer.utils.ProtectPluginsAPI;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;

@Plugin(id = "dupefixer",
	name = "DupeFixer",
	version = "1.3",
	authors = "Mr_Krab",
	dependencies = {
		@Dependency(id = "griefprevention", optional = true),
		@Dependency(id = "redprotect", optional = true)
	})
public class DupeFixer {
	
	@Inject
	@DefaultConfig(sharedRoot = false)
	private Path defaultConfig;
	@Inject
	@ConfigDir(sharedRoot = false)
	private Path configDir;
	@Inject
	@ConfigDir(sharedRoot = false)
	private File configFile;
	private ConfigurationNode rootNode;
	private YAMLConfigurationLoader configLoader;

	private Logger logger;

	private static DupeFixer instance;
	private static ConfigUtil configUtil;
	private static ProtectPluginsAPI protectPluginsAPI;
	private static DropListener dropListener;
	private static InteractItemListenerFluidFixGP iGp;
	private static InteractItemListenerFluidFixRP iRp;
	private static PortalListener portalListener;
	private static ShiftClickListener shiftClickListener;
	
	boolean foundRP = false;
	List<String> enableListeners = new ArrayList<String>();

	public Path getConfigDir() {
		return configDir;
	}
	public File getConfigFile() {
		return configFile;
	}
	public ConfigurationNode getRootNode() {
		return rootNode;
	}
	public Logger getLogger() {
		return logger;
	}
	public static DupeFixer getInstance() {
		return instance;
	}
	public ProtectPluginsAPI getProtectPluginsAPI() {
		return protectPluginsAPI;
	}

	@Listener
	public void onPostInitialization(GamePostInitializationEvent event) {
		logger = (Logger)LoggerFactory.getLogger("DupeFixer");
		instance = this;
		configUtil = new ConfigUtil();
		load();
		configUtil.checkConfigVersion();
		protectPluginsAPI = new ProtectPluginsAPI();
		updateListeners();
	}

	@Listener
	public void onCompleteLoadServer(GameStartedServerEvent event) {
		if(foundRP) {
			protectPluginsAPI.setRedProtect(RedProtect.get());
			protectPluginsAPI.setRedProtectAPI(RedProtect.get().getAPI());
		}
	}
	
	@Listener
	public void onReload(GameReloadEvent event) {
		load();
		updateListeners();
	}

	public void load() {
		configLoader = YAMLConfigurationLoader.builder().setPath(configDir.resolve("config.yml")).setFlowStyle(FlowStyle.BLOCK).build();
		try {
			rootNode = configLoader.load();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void updateListeners() {
		if(rootNode.getNode("FixContainer", "Enable").getBoolean() && !enableListeners.contains("DropListener")) {
			dropListener = new DropListener(this);
			Sponge.getEventManager().registerListeners(this, dropListener);
			enableListeners.add("DropListener");
		} else {
			if(dropListener != null) {
				Sponge.getEventManager().unregisterListeners(dropListener);
				enableListeners.remove("DropListener");
				dropListener = null;
			}
		}
		
		if(rootNode.getNode("FixFluidDupe", "Enable").getBoolean()) {
			iRp = new InteractItemListenerFluidFixRP(this);
			if (Sponge.getPluginManager().isLoaded("griefprevention") && !enableListeners.contains("InteractItemListenerFluidFixGP")) {
				protectPluginsAPI.setGriefPreventionAPI(GriefPrevention.getApi());
				iGp = new InteractItemListenerFluidFixGP(this);
				Sponge.getEventManager().registerListeners(this, iGp);
				enableListeners.add("InteractItemListenerFluidFixGP");
			} else {
				if(iGp != null) {
					Sponge.getEventManager().unregisterListeners(iGp);
					enableListeners.remove("InteractItemListenerFluidFixGP");
					iGp = null;
				}
			}
			/*
			 * Руки оторвать бы разработчику RedProtect за то, как он предоставляет API своего плагина.
			 * The RedProtect developer needs to tear off his hands for how he provides his plugin API.
			 */
			if(Sponge.getPluginManager().getPlugin("redprotect").isPresent() && !enableListeners.contains("InteractItemListenerFluidFixRP")) {
				foundRP = true;
				Sponge.getEventManager().registerListeners(this, iRp);
				enableListeners.add("InteractItemListenerFluidFixRP");
			} else {
				if(iRp != null) {
					Sponge.getEventManager().unregisterListeners(iRp);
					enableListeners.remove("InteractItemListenerFluidFixRP");
					iRp = null;
				}
			}
			if(!protectPluginsAPI.isPresentGP() && !foundRP) {
				logger.error("None of the supported claims protection plugins were found!");
			}
		}
		if(rootNode.getNode("BlockShiftClick", "Enable").getBoolean() && !enableListeners.contains("ShiftClickListener")) {
			shiftClickListener = new ShiftClickListener(this);
			Sponge.getEventManager().registerListeners(this, shiftClickListener);
			enableListeners.add("ShiftClickListener");
		} else {
			if(shiftClickListener != null) {
				Sponge.getEventManager().unregisterListeners(shiftClickListener);
				enableListeners.remove("ShiftClickListener");
				shiftClickListener = null;
			}
		}
		if(rootNode.getNode("PortalLock", "Enable").getBoolean() && !enableListeners.contains("PortalListener")) {
			portalListener = new PortalListener(this);
			Sponge.getEventManager().registerListeners(this, portalListener);
			enableListeners.add("PortalListener");
		} else {
			if(portalListener != null) {
				Sponge.getEventManager().unregisterListeners(portalListener);
				enableListeners.remove("PortalListener");
				portalListener = null;
			}
		}
	}
}
