# Clean Screenshot

A RuneLite plugin that saves a PNG of the game canvas with every RuneLite overlay removed. Press a hotkey and you get just the game itself: no title bar, no side panel, no other windows, and none of the overlays drawn by plugins (Ground Items, Player Indicators, NPC Indicators, tile markers, infoboxes, tooltips and so on). Everything the vanilla game draws on its own, such as your character, NPCs, HP bars, hitsplats, overhead text and prayers, chat, inventory and minimap, stays in the picture.

## Features

- Captures the game canvas only, not the RuneLite window frame or side panel.
- Strips all RuneLite overlays from the image.
- Keeps everything the vanilla game renders itself.
- Saves timestamped files and never overwrites an existing screenshot.
- Optional notification when a screenshot is taken.
- Optional copy of the image to the clipboard.

## How to use

1. Enable **Clean Screenshot** in the RuneLite plugin list.
2. Open the plugin's configuration panel (the cog icon) and set **Screenshot hotkey**. It is unset by default, so nothing happens until you pick a key.
3. Log in and press the hotkey in game.
4. Find the image in the standard RuneLite screenshots folder, in a `Clean Screenshots` subfolder of your character's folder:

   ```
   %USERPROFILE%\.runelite\screenshots\<player name>\Clean Screenshots\
   ```

   On Linux and macOS this is `~/.runelite/screenshots/<player name>/Clean Screenshots/`. On Leagues, Deadman and other special worlds the character folder is named `<player name>-<world type>`.

Tip: bind a modifier combination such as `Ctrl+Shift+S`. The hotkey's key is consumed by the plugin, so a bare letter takes a screenshot instead of being typed in chat, and an F-key stops opening its game tab. Avoid Alt-based combinations (Alt is RuneLite's overlay-move key) and do not reuse the hotkey of the core Screenshot plugin or another plugin, because only one listener receives the key.

## Configuration

| Option | Default | Description |
| --- | --- | --- |
| Screenshot hotkey | Unset | Key (or key combination) that takes a clean screenshot. |
| Notify on capture | On | Shows a RuneLite notification when a screenshot has been saved. |
| Copy to clipboard | Off | Also copies the screenshot to the system clipboard. |
| Overlays to keep | `FpsOverlay, WorldHopperPingOverlay, CurrentWorldOverlay, PingGraphOverlay` | Comma-separated overlay names that stay in the screenshot. The defaults keep the FPS counter (FPS Control), the ping displays (World Hopper and Ping Graph) and the world number (Current World, from the Plugin Hub). Add the name of any other overlay you want to keep. Clear the field to hide every overlay. |

## How it works

When the hotkey is pressed (and only while you are logged in), the plugin temporarily detaches all overlays (except those on the keep list) from RuneLite's `OverlayManager` for about two rendered frames. It captures the next frame, which is fully overlay-free, and then re-attaches the overlays straight away. The image is written with RuneLite's own `ImageCapture` helper, so it lands in the normal screenshots folder with a timestamped file name.

## Limitations

- **Overlays blink off briefly.** All overlays disappear for roughly one to two frames when you press the hotkey.
- **Render-level changes are not undone.** The plugin only removes overlays. Plugins that change what the game renders itself still affect the image. Examples: Entity Hider (hidden players and NPCs stay hidden), GPU or 117HD style rendering changes, and Interface Styles or resource packs.
- **Hitboxes and outlines will not appear.** The vanilla game does not draw hitboxes, so any hitbox or outline comes from a plugin overlay and is removed. Hitsplats are drawn by the vanilla game and do appear.
- **Some client-drawn effects may remain.** RuneLite-drawn elements that are not part of the overlay system, such as the full-screen notification flash if it is active or the side-panel open/close arrow when the title bar is hidden, can still show up in the image.
- **Output size follows your display settings.** The image has the size of the game canvas, scaled by Stretched Mode and Windows display scaling.
- **A press during a capture, or while loading, is ignored.** Press the hotkey again. Changing region or world also cancels a capture in progress.
- **Rare overlay reappearing.** If another plugin removes one of its overlays, or is disabled, within the same two frames as a capture, that overlay may reappear until the plugin is toggled off and on.
- **Logged-in only.** The hotkey does nothing on the login screen or while loading.
- **The hotkey key is consumed.** A plain-letter hotkey is swallowed instead of being typed in chat, and an F-key hotkey overrides the game's F-key tab shortcut. Use a modifier combination instead, and avoid Alt.

## Compliance and privacy

- No network access: nothing is uploaded or sent anywhere.
- No file I/O of its own: images are saved through RuneLite's `ImageCapture` helper.
- No reflection and no extra dependencies.
- No input injection: the plugin never sends keys or clicks to the game. It only listens for the hotkey you configure.

## Contributing and license

Issues and pull requests are welcome. Released under the BSD 2-Clause License; see [LICENSE](LICENSE).
