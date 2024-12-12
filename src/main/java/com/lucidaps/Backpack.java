package com.lucidaps;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Backpack implements InventoryHolder {
	private static String titleFormat = "Backpack";
	private static String titleOtherFormat = "{OwnerName}'s Backpack";

	// Map to keep track of backpack inventories
	private static final Map<Inventory, Backpack> inventoryBackpackMap = new ConcurrentHashMap<>();

	private final UUID ownerId;
	private final Map<Player, Boolean> opened = new ConcurrentHashMap<>();
	private Inventory bp;
	private boolean hasChanged;
	private int ownerDatabaseId = -1;

	public static void setTitle(@NotNull String title, @NotNull String titleOther) {
		titleFormat = title;
		titleOtherFormat = titleOther;
	}

	public Backpack(OfflinePlayer owner) {
		this(owner, 9, -1);
	}

	public Backpack(OfflinePlayer owner, int size) {
		this(owner, size, -1);
	}

	public Backpack(OfflinePlayer owner, int size, int ID) {
		this.ownerId = owner.getUniqueId();
		this.ownerDatabaseId = ID;
		String name = owner.getName() != null ? owner.getName() : "Unknown";

		// Decide which title to use
		String title;
		if (owner.getPlayer() != null && owner.getPlayer().isOnline()) {
			title = titleFormat.replace("{OwnerName}", name);
		} else {
			title = titleOtherFormat.replace("{OwnerName}", name);
		}

		// Translate color codes
		title = ChatColor.translateAlternateColorCodes('&', title);

		// Ensure title length does not exceed 32 characters
		if (title.length() > 32) title = title.substring(0, 32);

		bp = Bukkit.createInventory(this, size, title);
	}

	public Backpack(OfflinePlayer owner, @NotNull org.bukkit.inventory.ItemStack[] backpack, int ID) {
		this(owner, backpack.length, ID);
		bp.setContents(backpack);
	}

	public @NotNull OfflinePlayer getOwner() {
		return Bukkit.getOfflinePlayer(ownerId);
	}

	public @Nullable Player getOwnerPlayer() {
		return Bukkit.getPlayer(ownerId);
	}

	public void open(@NotNull Player player, boolean editable) {
		opened.put(player, editable);
		player.openInventory(bp);
		// Register the inventory
		inventoryBackpackMap.put(bp, this);
	}

	public void open(@NotNull Player player, boolean editable, @Nullable String title) {
		opened.put(player, editable);
		if (title != null && title.length() > 32) title = title.substring(0, 32);
		if (title != null) {
			// Translate color codes in the custom title
			title = ChatColor.translateAlternateColorCodes('&', title);
			Inventory inv = Bukkit.createInventory(this, bp.getSize(), title);
			inv.setContents(bp.getContents());
			player.openInventory(inv);
			// Register the new inventory
			inventoryBackpackMap.put(inv, this);
		} else {
			player.openInventory(bp);
			// Register the inventory
			inventoryBackpackMap.put(bp, this);
		}
	}

	public void close(Player p) {
		opened.remove(p);
		// Optionally, close the inventory for the player
		p.closeInventory();
	}

	public void closeAll() {
		for (Player p : opened.keySet()) {
			p.closeInventory();
		}
		opened.clear();
		save();
	}

	public boolean isOpen() {
		return !opened.isEmpty();
	}

	public boolean canEdit(@NotNull Player player) {
		return opened.getOrDefault(player, false);
	}

	@Override
	public @NotNull Inventory getInventory() {
		return bp;
	}

	public boolean hasChanged() {
		return hasChanged;
	}

	public void setChanged() {
		this.hasChanged = true;
	}

	public void save() {
		if (hasChanged) {
			Minepacks.getInstance().getDatabase().saveBackpack(this);
			hasChanged = false;
		}
	}

	public void forceSave() {
		hasChanged = true;
		save();
	}

	public void clear() {
		bp.clear();
		setChanged();
		save();
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public int getOwnerDatabaseId() {
		return ownerDatabaseId;
	}

	public void setOwnerDatabaseId(int ownerDatabaseId) {
		this.ownerDatabaseId = ownerDatabaseId;
	}

	public int getSize() {
		return bp.getSize();
	}

	// Method to retrieve Backpack from Inventory
	public static Backpack fromInventory(Inventory inventory) {
		return inventoryBackpackMap.get(inventory);
	}

	// Method to unregister an Inventory when closed
	public static void unregisterInventory(Inventory inventory) {
		inventoryBackpackMap.remove(inventory);
	}
}
