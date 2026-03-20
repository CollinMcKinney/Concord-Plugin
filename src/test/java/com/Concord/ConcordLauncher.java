package com.Concord;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ConcordLauncher
{
	public static void main(String[] args) throws Exception
	{
		// TODO: yellow warning - Unchecked generics array creation for varargs parameter.
		ExternalPluginManager.loadBuiltin(ConcordPlugin.class);
		RuneLite.main(args);
	}
}