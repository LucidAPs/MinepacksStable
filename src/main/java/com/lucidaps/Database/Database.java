package com.lucidaps.Database;

import com.lucidaps.Backpack;
import com.lucidaps.Minepacks;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Database implements Listener {
	protected final Minepacks plugin;
	private final Map<UUID, Backpack> backpacks = new ConcurrentHashMap<>();

	public Database(Minepacks plugin) {
		this.plugin = plugin;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	public void close() {
		HandlerList.unregisterAll(this);
		for (Backpack bp : backpacks.values()) {
			bp.forceSave();
		}
		backpacks.clear();
	}

	/**
	 * Returns a backpack from cache or loads it from database if not cached.
	 */
	public Backpack getBackpack(OfflinePlayer player) {
		if (player == null) return null;
		Backpack bp = backpacks.get(player.getUniqueId());
		if (bp == null) {
			bp = loadBackpack(player);
			if (bp == null) {
				// Create a new empty backpack if not found in DB
				bp = new Backpack(player);
				saveBackpack(bp);
			}
			backpacks.put(player.getUniqueId(), bp);
		}
		return bp;
	}

	public void replaceBackpack(OfflinePlayer player, Backpack newBackpack) {
		if (player != null && newBackpack != null) {
			backpacks.put(player.getUniqueId(), newBackpack);
		}
	}

	/**
	 * Returns a collection of currently loaded backpacks.
	 */
	public Collection<Backpack> getLoadedBackpacks() {
		return backpacks.values();
	}

	/**
	 * Unloads a backpack from the cache and saves it if necessary.
	 */
	public void unloadBackpack(Backpack backpack) {
		// If you want to force save on unload or ensure changes are saved:
		backpack.save();
		backpacks.remove(backpack.getOwnerId());
	}

	/**
	 * Save a backpack to the database.
	 */
	public abstract void saveBackpack(Backpack backpack);

	/**
	 * Load a backpack from the database.
	 */
	protected abstract Backpack loadBackpack(OfflinePlayer player);

	/**
	 * Update player info in database if needed.
	 */
	public abstract void updatePlayer(Player player);
}