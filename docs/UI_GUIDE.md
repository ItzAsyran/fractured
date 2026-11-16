# Fragmented: The Slime Core (UI Architecture & Developer Guide)

This guide documents the centralized **Slime Core UI**, its Bedrock "Ore UI" design system, the contextual 3D Stage, and how to add new settings, cards, and tabs as the mod grows.

---

## 1. Overview & Architecture

The Slime Core (`SlimeCoreScreen`) replaces Cloth Config and chat commands with a unified, 3D-interactive in-game hub:

```
[ Open Slime Core ]
  ├── In-Game Keybind: 'K' (key.slimeform.open_core)
  ├── Command: /slime (or /slime gui, /slime menu)
  └── Mod Menu: Config Screen factory (SlimeFormModMenu)
```

### Screen Layout Structure
* **Header Bar (32px):** Mod brand on left, centered navigation tab buttons (`BedrockTabButton`), and close button `(X)`.
* **Left Stage (38% screen width):** `SlimeStageWidget` - 3D living entity viewport with mouse-drag orbit (yaw/pitch) and zoom controls.
* **Right Console (60% screen width):** Scissored scroll container hosting grouped `BedrockCard` instances for the active tab.
* **Footer Bar (34px):** Quick global actions: `Reset Config` and `Done / Save`.

---

## 2. Design System: Bedrock "Ore UI"

The UI uses a light, translucent floating card aesthetic matching Minecraft: Bedrock Edition:

| Element | Color / Token | Usage |
| :--- | :--- | :--- |
| **Card Background** | `0x16FFFFFF` | Soft translucent white card surface |
| **Card Outline** | `0x38FFFFFF` | 1px crisp card border |
| **Active Slime Accent** | `0xFF47D147` | Pill switch active track, slider fill, highlights |
| **Inactive Track** | `0x33FFFFFF` | Pill switch & slider background track |
| **Card Title Header** | `0xFF47D147` | Green section titles with `✦ ` prefix |
| **Background Scrim** | `extractTransparentBackground()` | Translucent in-game backdrop |

### UI Component Primitives (`io.asy.fragmented.gui.components`)

* **`BedrockCard`:** Grouped container with rounded borders, title bar, and auto-layout for children. Supports collapsible accordion mode (used for Developer Mode) and handles focused-child dispatch for dragging without audio duplicate events.
* **`BedrockSwitchWidget`:** Pill-shaped toggle switch (30×16 px) with sliding white knob and clean single vanilla click audio feedback.
* **`BedrockSliderWidget`:** Sleek 4px line slider with active slime-green progress fill, value snapping/locking to discrete steps (integer steps or floating increments), and reliable mouse dragging.
* **`BedrockButton`:** Clean rounded card button with `PRIMARY` (green), `NEUTRAL` (translucent), or `DANGER` (red) styles using standard vanilla UI button click sounds.
* **`BedrockTabButton`:** Pill tab button with active bottom emerald line.

---

## 3. The 3 Consolidated Tabs

1. **`GameplayMechanicsTab` (`⚙ Gameplay & Powers`):**
   * **Slime Form Core:** Big active toggle switch, auto-activate on join, max slime size slider (1-10), slime balls required slider.
   * **Powers & Morph:** Slime morph switch, quick trigger morph button, custom morph size slider, transform/exit durations.
   * **Survival & Recovery:** Split duration slider, reform safety radius slider, hostile block switch.
   * **World Habits:** Slime water buoyancy switch, AFK dormant switch, AFK delay slider.

2. **`AppearanceAudioTab` (`🎨 Look & Sounds`):**
   * **Player Avatar Shaders:** Transparency slider (0-100%), slime tint switch, outer slime shell switch, footstep particles. (Synchronized to tracking players via `SlimeFormClient.syncPlayerAppearance()`).
   * **Mounted Slime Alignment:** Rider X, Y, Z offset sliders with live 3D view.
   * **Floating Items in Belly:** Floating items toggle, scale slider, rotation X slider, bob amplitude slider.
   * **Slime Soundboard:** Master sounds switch, plus individual toggles and **🔊 Preview** buttons for Jump, Landing, Footsteps, Hurt, Death, Attack, and Size-Up sounds.

3. **`TheLabTab` (`🔬 The Lab & Diagnostics`):**
   * **Safe-Zone Calibration Wizard:** Visual 3-step tool replacing chat number typing. Select orientation (West-East / North-South), click "Sample Top-Left" and "Sample Bottom-Right" from player position, and click "Commit Calibrated Zone" to push bounds to server.
   * **Experimental Features:** Block phasing (`doPhase`), slime-chunk aura, slime-chunk chance %, swamp first spawn.
   * **Developer & Diagnostics Mode:** Collapsible accordion with master developer toggle hiding/revealing flee path lines, danger bossbar, lineage nametags, coordinate axes, and phase debug.

---

## 4. Contextual 3D Stage Engine (`SlimeStageWidget`)

The 3D stage dynamically switches preview rigs depending on the active setting using `GuiGraphicsExtractor.entity(...)`:

| Mode | Trigger | What Renders in 3D |
| :--- | :--- | :--- |
| `AVATAR_SHADERS` | Transparency, Tint, Shell | Player avatar with live transparency & jelly shell layers |
| `SLIME_SCALE` | Max Size, Slime Balls, Morph Size | Slime entity scaling dynamically to the exact selected size (max size or custom morph size) |
| `MOUNTED_RIDER` | Rider X, Y, Z offsets | Slime entity with the player model mounted atop it, interactively responding to rider offsets |
| `FLOATING_ITEMS` | Floating items, item scale/rot | Translucent size-2 slime showing internal item preview |
| `RECOVERY_RING` | Split duration, safety radius | Miniature size-1 baby slime |

### Mouse Controls:
* **Left-Click + Drag on Stage:** Rotates camera yaw 360° and tilts pitch up/down.
* **Scroll Wheel / Zoom Buttons:** Zooms viewport in and out (50% to 250%).
* **Reset View Button:** Restores default orientation and 100% zoom.

---

## 5. Developer Guide: How to Add Features

### A. Adding a New Setting to a Card
1. Add the field to `SlimeFormConfig.java`:
   ```java
   public boolean myFeature = true;
   ```
2. In the appropriate tab (e.g., `GameplayMechanicsTab.java`):
   ```java
   myCard.addSwitch(
       Component.literal("My Feature"),
       Component.literal("Tooltip explaining the feature."),
       config.myFeature,
       val -> {
           config.myFeature = val;
           SlimeFormConfig.save();
       },
       () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE) // optional stage focus
   );
   ```

### B. Adding a New Card to a Tab
Inside any tab's `buildCards()` method:
```java
BedrockCard newCard = new BedrockCard(x, y, width, Component.literal("My Card"));
newCard.addSlider(Component.literal("My Slider"), current, min, max, isInteger,
    val -> Component.literal("Value: " + val),
    val -> { /* save logic */ },
    null
);
cards.add(newCard);
```

### C. Adding a New 3D Viewport Mode
1. Add an entry to `SlimeStageWidget.PreviewMode`:
   ```java
   public enum PreviewMode {
       AVATAR_SHADERS("Avatar Shaders"),
       ...
       MY_NEW_MODE("My Mode");
   }
   ```
2. In `SlimeStageWidget#extractWidgetRenderState`, add the case:
   ```java
   case MY_NEW_MODE -> {
       // Render your custom living entity or rig using renderStateOnStage:
       EntityRenderState state = getOrCreateSlimeRenderState(client, size, 180.0F);
       renderStateOnStage(graphics, x1, y1, x2, y2, scale, xOffset, yOffset, zOffset, state);
   }
   ```
   *Note: Both in-world and Main Menu preview are fully supported by creating decoupled render states.*
3. Set the mode from any UI callback: `stage.setPreviewMode(SlimeStageWidget.PreviewMode.MY_NEW_MODE);`.
