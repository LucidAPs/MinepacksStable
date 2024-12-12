package com.lucidaps.Command;

import com.lucidaps.Backpack;
import com.lucidaps.Minepacks;
import com.lucidaps.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {
	private final Minepacks plugin;

	public CommandManager(Minepacks plugin) {
		this.plugin = plugin;

		PluginCommand cmd = plugin.getCommand("backpack");
		if (cmd != null) {
			cmd.setExecutor(this);
			cmd.setTabCompleter(this);
		}
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
							 @NotNull String label, @NotNull String[] args) {
		String noPermission = plugin.getConfig().getString("Language.Ingame.NoPermission", "&cYou don't have the permission to do that.");
		String notFromConsole = plugin.getConfig().getString("Language.NotFromConsole", "&cCommand not usable from console!");
		String invalidSubcommand = "&cInvalid subcommand! Use /bp [open|clear|reload] [player_name].";

		// Base /bp command (open own backpack)
		if (args.length == 0) {
			if (sender instanceof Player) {
				Player player = (Player) sender;

				// Check if player's game mode is allowed
				if (!plugin.isGameModeAllowed(player)) {
					String wrongGameModeMessage = plugin.getConfig().getString("Language.Ingame.Open.WrongGameMode",
							"&cYou are not allowed to open your backpack in your current game mode.");
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', wrongGameModeMessage));
					return true;
				}

				if (!player.hasPermission(Permissions.USE)) {
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
					return true;
				}
				plugin.openBackpack(player, player, true);
			} else {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', notFromConsole));
			}
			return true;
		}

		// Handle subcommands
		String subcommand = args[0].toLowerCase();
		String[] subArgs = Arrays.copyOfRange(args, 1, args.length); // Exclude subcommand
		switch (subcommand) {
			case "open":
				handleOpenCommand(sender, subArgs);
				break;

			case "clear":
				handleClearCommand(sender, subArgs);
				break;

			case "reload":
				handleReloadCommand(sender, subArgs);
				break;

			default:
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidSubcommand));
		}
		return true;
	}

	private void handleOpenCommand(@NotNull CommandSender sender, @NotNull String[] args) {
		String noPermission = plugin.getConfig().getString("Language.Ingame.NoPermission", "&cYou don't have the permission to do that.");
		String invalidBackpack = plugin.getConfig().getString("Language.Ingame.InvalidBackpack", "&cInvalid backpack.");
		String usage = "&cUsage: /bp open [player_name]";

		if (args.length == 0) {
			// Open sender's own backpack
			if (sender instanceof Player) {
				Player player = (Player) sender;

				// Check if player's game mode is allowed
				if (!plugin.isGameModeAllowed(player)) {
					String wrongGameModeMessage = plugin.getConfig().getString("Language.Ingame.Open.WrongGameMode",
							"&cYou are not allowed to open your backpack in your current game mode.");
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', wrongGameModeMessage));
					return;
				}

				if (!player.hasPermission(Permissions.USE)) {
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
					return;
				}
				plugin.openBackpack(player, player, true);
			} else {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "Command not usable from console!"));
			}
		} else if (args.length == 1) {
			// Open another player's backpack
			if (!sender.hasPermission(Permissions.OTHERS)) {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
				return;
			}
			String targetName = args[0];
			OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

			// **Check if the player exists (has played before or is online)**
			if (!target.hasPlayedBefore() && !target.isOnline()) {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPlayer '" + targetName + "' does not exist or has never joined the server."));
				return;
			}

			// Open the backpack
			if (sender instanceof Player) {
				Player player = (Player) sender;
				boolean editable = player.hasPermission(Permissions.OTHERS_EDIT);
				plugin.openBackpack(player, target, editable);
			} else {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidBackpack));
			}
		} else {
			sender.sendMessage(ChatColor.translateAlternateColorCodes('&', usage));
		}
	}

	private void handleClearCommand(@NotNull CommandSender sender, @NotNull String[] args) {
		String noPermission = plugin.getConfig().getString("Language.Ingame.NoPermission", "&cYou don't have the permission to do that.");
		String invalidBackpack = plugin.getConfig().getString("Language.Ingame.InvalidBackpack", "&cInvalid backpack.");
		String usage = "&cUsage: /bp clear [player_name]";

		OfflinePlayer target;

		if (args.length == 0) {
			// Clear sender's own backpack
			if (sender instanceof Player) {
				Player player = (Player) sender;
				if (!player.hasPermission(Permissions.CLEAR)) { // Assuming CLEAR permission for clearing own backpack
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
					return;
				}
				target = player;
			} else {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', usage));
				return;
			}
		} else if (args.length == 1) {
			// Clear another player's backpack
			if (!sender.hasPermission(Permissions.OTHERS)) {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
				return;
			}

			String targetName = args[0];
			target = Bukkit.getOfflinePlayer(targetName);

			// **Check if the player exists (has played before or is online)**
			if (!target.hasPlayedBefore() && !target.isOnline()) {
				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPlayer '" + targetName + "' does not exist or has never joined the server."));
				return;
			}
		} else {
			// Incorrect usage
			sender.sendMessage(ChatColor.translateAlternateColorCodes('&', usage));
			return;
		}

		// Fetch the backpack from the database
		Backpack bp = plugin.getDatabase().getBackpack(target);
		if (bp == null) {
			// This should rarely happen since getBackpack creates a new one if it doesn't exist
			sender.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidBackpack));
			return;
		}

		// Clear the backpack
		bp.clear();

		// Notify the sender
		sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aBackpack cleared successfully for " + target.getName() + "!"));

		// Optionally, notify the target player if they are online and are not the sender
		if (target.isOnline() && !target.getPlayer().equals(sender)) {
			Player targetPlayer = target.getPlayer();
			if (targetPlayer != null) {
				targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour backpack has been cleared by " + sender.getName() + "!"));
			}
		}
	}

	private void handleReloadCommand(@NotNull CommandSender sender, @NotNull String[] args) {
		if (!sender.hasPermission(Permissions.RELOAD)) {
			sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Language.Ingame.NoPermission", "&cNo permission.")));
			return;
		}
		sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Language.Ingame.Reload.Reloading", "&1Reloading Minepacks...")));
		plugin.reloadConfig();
		sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Language.Ingame.Reload.Reloaded", "&1Minepacks reloaded!")));
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
									  @NotNull String alias, @NotNull String[] args) {
		if (args.length == 1) {
			// Initialize a mutable list to store subcommands
			List<String> subcommands = new ArrayList<>();

			// Add subcommands based on permissions
			if (sender.hasPermission(Permissions.OTHERS)) {
				subcommands.add("open");
			}
			if (sender.hasPermission(Permissions.CLEAR)) {
				subcommands.add("clear");
			}
			if (sender.hasPermission(Permissions.RELOAD)) {
				subcommands.add("reload");
			}

			// Filter the subcommands based on the current input
			String currentInput = args[0].toLowerCase();
			List<String> filteredSubcommands = new ArrayList<>();
			for (String subcommand : subcommands) {
				if (subcommand.startsWith(currentInput)) {
					filteredSubcommands.add(subcommand);
				}
			}

			return filteredSubcommands;
		} else if (args.length == 2 && "open".equalsIgnoreCase(args[0]) && sender.hasPermission(Permissions.OTHERS)) {
			// Autocomplete player names for the "open" subcommand if the sender has the "OTHERS" permission
			List<String> playerNames = new ArrayList<>();
			String currentInput = args[1].toLowerCase();
			for (Player player : Bukkit.getOnlinePlayers()) {
				String name = player.getName();
				if (name.toLowerCase().startsWith(currentInput)) {
					playerNames.add(name);
				}
			}
			return playerNames;
		}
		return null;
	}
}
