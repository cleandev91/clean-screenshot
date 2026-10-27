package com.cleanscreenshot;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CleanScreenshotPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CleanScreenshotPlugin.class);
		RuneLite.main(args);
	}
}
