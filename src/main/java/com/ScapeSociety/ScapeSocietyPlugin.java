package com.ScapeSociety;

import javax.inject.Inject;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
	name = "Scape Society"
)
public class ScapeSocietyPlugin extends Plugin
{

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ScapeSocietyPanel panel;

	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		log.debug("Scape Society started!");
		log.info("Scape Society started!");

		panel = injector.getInstance(ScapeSocietyPanel.class);
		panel.init();
		panel.setPlugin(this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Scape Society")
				.priority(1)
				.icon(icon)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		log.debug("Scape Society stopped!");
	}
}
