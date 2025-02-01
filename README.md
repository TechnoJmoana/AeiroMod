# AeiroMod

**Version:** 1.2.1  
**Minecraft Version:** 1.8.9  
**Author:** TechnoJ

AeiroMod is a lightweight Minecraft mod that provides chat-based commands and an adaptive in-game GUI for managing carries, auto-responders, and other settings. The mod also features an auto-remove semicolons option and smart chat interception.

----AI CONTENT DISCLAIMER----
**I use AI to help code AeiroMod. If you have a problem with this, feel free not to use my mod**

## Features

- **Chat-based Commands:**  
  Use in-game commands (starting with `?`) to manage carries, set bio, and control auto-responders.

- **Custom Responders:**  
  Set up custom auto-responses triggered by specific phrases. Responder modes include ENABLED, DISABLED, and CARRY-ONLY.

- **Carry Mode Management:**  
  Toggle carry mode and auto-carry mode via commands to manage carry lists.

- **Adaptive In-Game GUI:**  
  An adaptive GUI (`GuiResponders`) that supports many responders by dynamically calculating visible rows and providing scrolling.

- **Auto-remove Semicolons:**  
  Optionally remove semicolons from chat messages automatically before they are sent.

- **Enhanced Chat Screen:**  
  A custom chat screen (`MyGuiChat`) that intercepts outgoing commands, pre-populates the slash (/) when needed, and processes auto-responses.

## Installation

1. **Download & Build:**

   - Clone the repository:
     ```bash
     git clone https://github.com/yourusername/AeiroMod.git
     ```
   - Open the project in your preferred Java IDE (e.g., Eclipse or IntelliJ IDEA).
   - Ensure you have Minecraft Forge 1.8.9 and its dependencies set up.
   - Build the mod using your build tool (Gradle or Maven, as configured).

2. **Install the Mod:**

   - Copy the built JAR file into your Minecraft `mods` folder.
   - Launch Minecraft with Forge 1.8.9.

## Usage

### Chat Commands

- `?carry <user> <diff> <amount>`  
  Initialize a carry for a user with a specified difficulty (e.g., `m5`) and amount.

- `?addcarry <user> <diff> <amt>`  
  Add to an existing carry.

- `?delcarry <index>`  
  Remove a carry entry by index.

- `?finished`  
  Log a finished carry run.

- `?list`  
  List active carries.

- `?help`  
  Display a list of available commands.

- `?bio` and `?setbio <text>`  
  View and set your user bio.

- `?setresponder <phrase>`  
  Create a new auto-responder by setting a phrase and capturing the next line you type.

- `?listresponders`  
  List all auto-responders.

- `?delresponder <index>`  
  Remove a responder by index.

- `?updresponder <index> <new response>`  
  Update an existing responder’s response.

- `?price <m1..m7> <sbm|sbz> [amount]`  
  Get price information for carries.

- `?carrymode`  
  Toggle carry mode.

### In-Game GUI

- **Opening the GUI:**  
  Press the key bound to "Open AeiroMod GUI" (default is `R`) to open the mod’s GUI.
  
- **Pages:**  
  The GUI contains two pages:
  - **Autoresponders:**  
    Manage custom responders, add, remove, or update them.
  - **Other Settings:**  
    Toggle carry mode, auto-carry mode, auto-remove semicolons, and update your bio.

- **Adaptive Layout:**  
  The GUI automatically adapts to the screen size, allowing scrolling if many responders exist.

### Chat Screen Enhancements

- When you press the slash (`/`) key, the chat screen pre-populates with `/` automatically (if opened by slash).  
- The mod intercepts outgoing messages starting with `?` to process commands locally.

## Configuration

All settings are stored in a configuration file located at:
<minecraft directory>/config/aeiromod.cfg

Settings such as `carryMode`, `autoCarryMode`, `autoRemoveSemicolons`, and custom responders are persisted between sessions.

## Troubleshooting

- **No Slash on Hypixel:**  
  The mod detects if the chat was opened by pressing `/` and forces the slash to appear. If running on Hypixel and the slash isn’t showing, check the key input and configuration settings.

- **Unexpected Messages:**  
  If extra messages appear (such as `AeiroMod > s`), verify that auto-responder checks do not process slash commands by ensuring the mod ignores chat messages starting with `/`.

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request with your improvements or bug fixes.

## License

[MIT License](LICENSE)
