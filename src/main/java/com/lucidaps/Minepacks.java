package com.lucidaps;

import com.lucidaps.Command.CommandManager;
import com.lucidaps.Database.Database;
import com.lucidaps.Database.SQLite;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Minepacks extends JavaPlugin {
	private static Minepacks instance;
	private Database database;

	private Sound openSound;
	private Sound closeSound;
	private boolean soundsEnabled;
	private float soundVolume;
	private float soundPitch;
	private int maxSize;
	private CommandManager commandManager;
	private Set<GameMode> allowedGameModes;
	private String latestVersion = "";
	private volatile boolean storageHealthy = true;

	public static Minepacks getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		instance = this;

		// Load default config if not present
		saveDefaultConfig();

		// Load AllowedGameModes from config
		loadAllowedGameModes();

		// Config values
		this.maxSize = getConfig().getInt("MaxSize", 6);
		String bpTitle = getConfig().getString("BackpackTitle", "Backpack");
		String bpTitleOther = getConfig().getString("BackpackTitleOther", "{OwnerName}'s Backpack");
		Backpack.setTitle(bpTitle, bpTitleOther);

		// Sound settings
		this.soundsEnabled = getConfig().getBoolean("Sound.Enabled", true);
		this.soundVolume = (float) getConfig().getDouble("Sound.Volume", 1.0);
		this.soundPitch = (float) getConfig().getDouble("Sound.Pitch", 1.0);
		String openSoundName = getConfig().getString("Sound.OpenSound", "BLOCK_CHEST_OPEN");
		String closeSoundName = getConfig().getString("Sound.CloseSound", "BLOCK_CHEST_CLOSE");

		try {
			openSound = (openSoundName != null && !"disabled".equalsIgnoreCase(openSoundName))
					? Sound.valueOf(openSoundName.toUpperCase())
					: null;
			closeSound = (closeSoundName != null && !"disabled".equalsIgnoreCase(closeSoundName))
					? Sound.valueOf(closeSoundName.toUpperCase())
					: null;
		} catch (IllegalArgumentException e) {
			getLogger().warning("Invalid sound name in config.");
		}

		// Initialize database (SQLite only)
		this.database = new SQLite(this);

		// Register command
		this.commandManager = new CommandManager(this);

		// Register event listener for backpack close
		getServer().getPluginManager().registerEvents(new BackpackListener(this), this);

		// Check if update checking is enabled in the config
		boolean isUpdateCheckEnabled = this.getConfig().getBoolean("checkForUpdates", true);
		if (isUpdateCheckEnabled) {
			new UpdateChecker(this, 121240).getVersion(version -> {
				latestVersion = version; // Store the latest version
				if (!this.getDescription().getVersion().equalsIgnoreCase(version)) {
					getLogger().info("There is a new update available.");
				}
			});
		}

		getLogger().info("Minepacks enabled.");
	}

	private void loadAllowedGameModes() {
		List<String> modes = getConfig().getStringList("AllowedGameModes");
		allowedGameModes = new HashSet<>();

		for (String mode : modes) {
			try {
				GameMode gameMode = GameMode.valueOf(mode.toUpperCase());
				allowedGameModes.add(gameMode);
			} catch (IllegalArgumentException e) {
				getLogger().warning("Invalid game mode in AllowedGameModes: " + mode);
			}
		}

		if (allowedGameModes.isEmpty()) {
			getLogger().warning("No valid game modes found in AllowedGameModes. Defaulting to SURVIVAL.");
			allowedGameModes.add(GameMode.SURVIVAL);
		}
	}

	public boolean isGameModeAllowed(Player player) {
		// If the player has 'backpack.admin', always allow opening the backpack
		if (player.hasPermission("backpack.admin")) {
			return true;
		}

		// Otherwise, only allow if current GameMode is in the 'allowedGameModes' set
		return allowedGameModes.contains(player.getGameMode());
	}

	/**
	 * Sends a formatted message to the player based on the configuration.
	 *
	 * @param player        The player to send the message to.
	 * @param messagePath   The path to the message in the config (e.g., "Language.Ingame.OwnBackpackClose").
	 * @param placeholders  A map of placeholders and their replacements.
	 */
	public void sendFormattedMessage(Player player, String messagePath, Map<String, String> placeholders) {
		String message = getConfig().getString(messagePath, "");
		String sendMethodPath = messagePath + "_SendMethod";
		String sendMethod = getConfig().getString(sendMethodPath, "chat").toLowerCase();

		if (message.isEmpty()) return;

		// Replace placeholders
		if (placeholders != null) {
			for (Map.Entry<String, String> entry : placeholders.entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue());
			}
		}

		// Translate color codes
		message = ChatColor.translateAlternateColorCodes('&', message);

		// Send the message based on the send method
		switch (sendMethod) {
			case "action_bar":
				player.sendActionBar(message);
				break;
			case "title":
				// Optionally, implement title messages
				player.sendTitle(message, "", 10, 70, 20);
				break;
			case "chat":
			default:
				player.sendMessage(message);
				break;
		}
	}

	// Getters for sound settings
	public boolean areSoundsEnabled() {
		return soundsEnabled;
	}

	public float getSoundVolume() {
		return soundVolume;
	}

	public float getSoundPitch() {
		return soundPitch;
	}

	public Sound getOpenSound() {
		return openSound;
	}

	public Sound getCloseSound() {
		return closeSound;
	}

	public Database getDatabase() {
		return database;
	}

	public int getBackpackPermSize(Player player) {
		int finalSize = 9;
		for (int i = 1; i <= maxSize; i++) {
			boolean hasPerm = player.hasPermission("backpack.size." + i);
			if (hasPerm) {
				finalSize = i * 9;
			}
		}
		return finalSize;
	}

	public boolean isStorageHealthy() {
		return storageHealthy;
	}

	public void markStorageFailure(Throwable throwable) {
		if (!storageHealthy) return;

		storageHealthy = false;

		getLogger().log(
				java.util.logging.Level.SEVERE,
				"Backpack storage failed. Backpacks are now locked to prevent duplication.",
				throwable
		);

		getServer().getScheduler().runTask(this, () -> {
			for (Player player : getServer().getOnlinePlayers()) {
				if (Backpack.fromInventory(player.getOpenInventory().getTopInventory()) != null) {
					player.closeInventory();
					player.sendMessage(ChatColor.RED + "Backpacks were locked because the server could not save data.");
				}
			}
		});
	}

	public void openBackpack(final Player opener, final OfflinePlayer owner, final boolean editable) {
		openBackpack(opener, owner, editable, null);
	}

	public void openBackpack(Player opener, OfflinePlayer owner, boolean editable, String title) {
		if (!isStorageHealthy()) {
			opener.sendMessage(ChatColor.RED + "Backpacks are temporarily locked because the server cannot save data.");
			return;
		}

		if (!isGameModeAllowed(opener)) {
			String wrongGameModeMessage = getConfig().getString(
					"Language.Ingame.Open.WrongGameMode",
					"&cYou are not allowed to open your backpack in your current game mode."
			);

			opener.sendMessage(ChatColor.translateAlternateColorCodes('&', wrongGameModeMessage));
			return;
		}

		Backpack backpack = database.getBackpack(owner);

		if (backpack == null) {
			opener.sendMessage(ChatColor.RED + "Backpack could not be loaded safely.");
			return;
		}

		if (owner.isOnline()) {
			Player ownerPlayer = owner.getPlayer();

			if (ownerPlayer != null) {
				int currentSize = backpack.getSize();
				int newSize = getBackpackPermSize(ownerPlayer);

				if (newSize > currentSize) {
					Backpack resized = resizeBackpack(backpack, newSize);

					if (resized == null) {
						opener.sendMessage(ChatColor.RED + "Backpack could not be resized safely.");
						return;
					}

					backpack = resized;
				}
			}
		}

		if (editable && backpack.hasOtherEditableViewer(opener)) {
			opener.sendMessage(ChatColor.RED + "That backpack is already being edited.");
			return;
		}

		openBackpack(opener, backpack, editable, title);
	}

	private Backpack resizeBackpack(Backpack oldBackpack, int newSize) {
		OfflinePlayer owner = oldBackpack.getOwner();
		Backpack newBackpack = new Backpack(owner, newSize, oldBackpack.getOwnerDatabaseId());

		ItemStack[] oldContents = oldBackpack.getInventory().getContents();
		ItemStack[] newContents = new ItemStack[newSize];

		for (int i = 0; i < Math.min(oldContents.length, newContents.length); i++) {
			newContents[i] = oldContents[i] == null ? null : oldContents[i].clone();
		}

		newBackpack.getInventory().setContents(newContents);
		newBackpack.setChanged();

		boolean saved = newBackpack.save();

		if (!saved) {
			markStorageFailure(new IllegalStateException("Failed to save resized backpack."));
			return null;
		}

		database.replaceBackpack(owner, newBackpack);
		return newBackpack;
	}

	public void openBackpack(@NotNull final Player opener, @NotNull final Backpack backpack, boolean editable, @Nullable String title) {
		if (!isStorageHealthy()) {
			opener.sendMessage(ChatColor.RED + "Backpacks are temporarily locked because the server cannot save data.");
			return;
		}
		if (backpack == null) {
			opener.sendMessage("Invalid backpack!");
			return;
		}
		if (areSoundsEnabled() && getOpenSound() != null) {
			opener.playSound(opener.getLocation(), getOpenSound(), getSoundVolume(), getSoundPitch());
		}
		backpack.open(opener, editable, title);
	}

	@Override
	public void onDisable() {
		if (database != null) {
			for (Backpack backpack : new java.util.ArrayList<>(database.getLoadedBackpacks())) {
				backpack.closeAll();
			}

			database.close();
		}

		HandlerList.unregisterAll(this);
		getServer().getScheduler().cancelTasks(this);
		getLogger().info("Minepacks disabled.");
		instance = null;
	}
}
