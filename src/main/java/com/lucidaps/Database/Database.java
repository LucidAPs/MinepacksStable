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

		boolean allSaved = true;

		for (Backpack bp : backpacks.values()) {
			boolean saved = bp.forceSave();

			if (!saved) {
				allSaved = false;
				plugin.getLogger().severe("Failed to save backpack for " + bp.getOwnerId() + " during shutdown!");
			}
		}

		if (allSaved) {
			backpacks.clear();
		} else {
			plugin.getLogger().severe("Some backpacks were not saved. Keeping cache until plugin shutdown completes.");
		}
	}

	/**
	 * Returns a backpack from cache or loads it from database if not cached.
	 */
	public Backpack getBackpack(OfflinePlayer player) {
		if (player == null) return null;

		Backpack bp = backpacks.get(player.getUniqueId());
		if (bp != null) {
			return bp;
		}

		bp = loadBackpack(player);

		if (bp == null) {
			if (!plugin.isStorageHealthy()) {
				plugin.getLogger().severe("Refusing to create empty backpack while storage is unhealthy: " + player.getUniqueId());
				return null;
			}

			bp = new Backpack(player);

			boolean saved = saveBackpack(bp);
			if (!saved) {
				plugin.getLogger().severe("Could not create new backpack safely: " + player.getUniqueId());
				return null;
			}
		}

		backpacks.put(player.getUniqueId(), bp);
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
	public boolean unloadBackpack(Backpack backpack) {
		if (!backpack.save()) {
			plugin.getLogger().severe("Refusing to unload backpack because save failed: " + backpack.getOwnerId());
			return false;
		}

		backpacks.remove(backpack.getOwnerId());
		return true;
	}

	/**
	 * Save a backpack to the database.
	 */
	public abstract boolean saveBackpack(Backpack backpack);

	/**
	 * Load a backpack from the database.
	 */
	protected abstract Backpack loadBackpack(OfflinePlayer player);

	/**
	 * Update player info in database if needed.
	 */
	public abstract void updatePlayer(Player player);
}