package com.ScapeSociety;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Scape Society"
)
public class ScapeSocietyPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ScapeSocietyConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Scape Society started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Scape Society stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Scape Society says " + config.greeting(), null);
		}
	}

	@Provides
	ScapeSocietyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ScapeSocietyConfig.class);
	}
}
