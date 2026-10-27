package com.cleanscreenshot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("cleanscreenshot")
public interface CleanScreenshotConfig extends Config
{
	@ConfigItem(
		keyName = "hotkey",
		name = "Screenshot hotkey",
		description = "Takes a screenshot of the game view without plugin overlays",
		position = 1
	)
	default Keybind hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "notifyOnCapture",
		name = "Notify on capture",
		description = "Show a notification when a screenshot is saved",
		position = 2
	)
	default boolean notifyOnCapture()
	{
		return true;
	}

	@ConfigItem(
		keyName = "copyToClipboard",
		name = "Copy to clipboard",
		description = "Also copy the screenshot to your clipboard",
		position = 3
	)
	default boolean copyToClipboard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "keepOverlays",
		name = "Overlays to keep",
		description = "Comma-separated overlay names that stay in the screenshot. The defaults keep the FPS counter, the ping displays and the current world display",
		position = 4
	)
	default String keepOverlays()
	{
		return "FpsOverlay, WorldHopperPingOverlay, CurrentWorldOverlay, PingGraphOverlay";
	}
}
