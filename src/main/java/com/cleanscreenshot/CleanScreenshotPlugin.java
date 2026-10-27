package com.cleanscreenshot;

import com.google.inject.Provides;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.ImageUtil;

/**
 * Saves a screenshot of the game view without plugin overlays.
 * <p>
 * Overlays are painted into the same buffer as the game, so they cannot be removed from a
 * finished frame. Instead, when the hotkey is pressed the overlays are detached from the
 * {@link OverlayManager}, the plugin waits for a frame that was drawn without them, and the
 * overlays are re-attached before the image is saved. Overlays on the keep list (by default
 * the FPS, ping and world displays) stay attached. Overlays are always put back: when the
 * frame arrives, after a short timeout, when the game state leaves LOGGED_IN, or on shutdown.
 */
@Slf4j
@PluginDescriptor(
	name = "Clean Screenshot",
	description = "Hotkey screenshot of the game view without any plugin overlays",
	tags = {"screenshot", "image", "capture", "clean", "overlay"}
)
public class CleanScreenshotPlugin extends Plugin
{
	/**
	 * Frames to wait before giving up and force-restoring the overlays.
	 */
	private static final int MAX_WAIT_FRAMES = 5;
	private static final String SCREENSHOT_SUB_DIR = "Clean Screenshots";
	private static final String SCREENSHOT_FILE_NAME = "Clean";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ImageCapture imageCapture;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private CleanScreenshotConfig config;

	// hidden, capturing and framesWaited are guarded by this: capture runs on the client
	// thread, but shutDown runs on the EDT
	private final List<Overlay> hidden = new ArrayList<>();
	// Set between startUp and shutDown, so a hotkey press queued on the client thread
	// cannot detach overlays after the plugin has been disabled
	private volatile boolean running;
	private boolean capturing;
	private int framesWaited;

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.hotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			// Called on the AWT thread; hop to the client thread to touch overlays
			clientThread.invokeLater(CleanScreenshotPlugin.this::beginCapture);
		}
	};

	// Kept in a field so that the same reference can be unregistered on shutdown
	private final Runnable frameListener = this::onFrame;

	@Override
	protected void startUp() throws Exception
	{
		running = true;
		keyManager.registerKeyListener(hotkeyListener);
		drawManager.registerEveryFrameListener(frameListener);
		log.debug("Clean Screenshot started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		running = false;
		keyManager.unregisterKeyListener(hotkeyListener);
		drawManager.unregisterEveryFrameListener(frameListener);
		// Never leave overlays detached
		restoreOverlays();
		log.debug("Clean Screenshot stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (capturing && gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("Capture cancelled by game state change to {}", gameStateChanged.getGameState());
			restoreOverlays();
		}
	}

	/**
	 * Detaches every overlay that is not on the keep list. Client thread.
	 */
	private synchronized void beginCapture()
	{
		if (!running || capturing || client.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("Ignoring screenshot request (running={}, capturing={}, state={})",
				running, capturing, client.getGameState());
			return;
		}

		capturing = true;
		framesWaited = 0;
		hidden.clear();
		final Set<String> keep = parseKeepList(config.keepOverlays());
		try
		{
			// Every overlay not on the keep list is detached and remembered
			overlayManager.removeIf(overlay -> !isKept(overlay, keep) && hidden.add(overlay));
		}
		catch (RuntimeException ex)
		{
			// Never leave the overlay list half detached
			log.warn("Unable to detach overlays for clean capture", ex);
			restoreOverlays();
			return;
		}
		log.debug("Detached {} overlays for clean capture", hidden.size());
	}

	private static Set<String> parseKeepList(String csv)
	{
		final Set<String> names = new HashSet<>();
		if (csv == null)
		{
			return names;
		}
		for (String name : csv.split(","))
		{
			final String trimmed = name.trim();
			if (!trimmed.isEmpty())
			{
				names.add(trimmed.toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	private static boolean isKept(Overlay overlay, Set<String> keep)
	{
		// Most overlays do not declare an owning plugin, so they are matched by their own
		// name (by default the overlay's class name)
		final String name = overlay.getName();
		return name != null && keep.contains(name.toLowerCase(Locale.ROOT));
	}

	/**
	 * Runs once per rendered frame on the client thread.
	 */
	private synchronized void onFrame()
	{
		if (!capturing)
		{
			return;
		}

		framesWaited++;

		if (framesWaited == 1)
		{
			// This frame may have been drawn before the overlays were detached
			return;
		}

		if (framesWaited == 2)
		{
			// Every-frame listeners run before DrawManager drains the next-frame queue in the
			// same processDrawComplete call, so onCleanFrame receives this frame's image.
			drawManager.requestNextFrameListener(this::onCleanFrame);
			return;
		}

		if (framesWaited > MAX_WAIT_FRAMES)
		{
			// DrawManager silently drops listeners if it cannot get an image
			log.debug("Timed out waiting for a clean frame, restoring overlays");
			restoreOverlays();
		}
	}

	/**
	 * Receives the overlay-free frame on the client thread.
	 */
	private synchronized void onCleanFrame(Image img)
	{
		if (!capturing)
		{
			// The watchdog already fired, so this frame may not be clean
			return;
		}

		final boolean notify = config.notifyOnCapture();
		final boolean clipboard = config.copyToClipboard();

		restoreOverlays();

		final BufferedImage shot = ImageUtil.bufferedImageFromImage(img);

		// Encoding and disk I/O are done by ImageCapture, off the client thread
		executor.submit(() -> imageCapture.saveScreenshot(shot, SCREENSHOT_FILE_NAME, SCREENSHOT_SUB_DIR, notify, clipboard));
	}

	/**
	 * Re-attaches every overlay that was detached by beginCapture.
	 */
	private synchronized void restoreOverlays()
	{
		try
		{
			for (Overlay overlay : hidden)
			{
				Plugin owner = overlay.getPlugin();
				if (owner != null && !pluginManager.isPluginActive(owner))
				{
					// The owning plugin was disabled during the capture window
					continue;
				}

				try
				{
					overlayManager.add(overlay);
				}
				catch (Exception ex)
				{
					// One misbehaving overlay must not stop the rest from being re-attached
					log.warn("Unable to re-attach overlay {}", overlay.getName(), ex);
				}
			}
		}
		finally
		{
			hidden.clear();
			capturing = false;
			framesWaited = 0;
		}
	}

	@Provides
	CleanScreenshotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CleanScreenshotConfig.class);
	}
}
