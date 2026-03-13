package com.ScapeSociety;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ScapeSocietyLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ScapeSocietyPlugin.class);
		RuneLite.main(args);
	}
}