package com.lucidaps;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
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

	private final Map<UUID, OpenSnapshot> snapshots = new ConcurrentHashMap<>();

	private static final class OpenSnapshot {
		private final ItemStack[] backpackContents;
		private final ItemStack[] storageContents;
		private final ItemStack[] armorContents;
		private final ItemStack offHand;
		private final ItemStack cursorItem;

		private OpenSnapshot(Player player, ItemStack[] backpackContents) {
			this.backpackContents = cloneContents(backpackContents);
			this.storageContents = cloneContents(player.getInventory().getStorageContents());
			this.armorContents = cloneContents(player.getInventory().getArmorContents());

			this.offHand = player.getInventory().getItemInOffHand() == null
					? null
					: player.getInventory().getItemInOffHand().clone();

			this.cursorItem = player.getItemOnCursor() == null
					? null
					: player.getItemOnCursor().clone();
		}
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
		open(player, editable, null);
	}

	public void open(@NotNull Player player, boolean editable, @Nullable String title) {
		opened.put(player, editable);

		if (editable) {
			snapshots.put(player.getUniqueId(), new OpenSnapshot(player, bp.getContents()));
		}

		if (title != null && title.length() > 32) {
			title = title.substring(0, 32);
		}

		if (title != null) {
			title = ChatColor.translateAlternateColorCodes('&', title);

			Inventory inv = Bukkit.createInventory(this, bp.getSize(), title);
			inv.setContents(cloneContents(bp.getContents()));

			inventoryBackpackMap.put(inv, this);
			player.openInventory(inv);
		} else {
			inventoryBackpackMap.put(bp, this);
			player.openInventory(bp);
		}
	}

	public void close(Player p) {
		p.closeInventory();
	}

	public void closeAll() {
		for (Player p : new ArrayList<>(opened.keySet())) {
			p.closeInventory();
		}
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

	public boolean save() {
		if (!hasChanged) {
			return true;
		}

		boolean saved = Minepacks.getInstance().getDatabase().saveBackpack(this);

		if (saved) {
			hasChanged = false;
		}

		return saved;
	}

	public boolean forceSave() {
		hasChanged = true;
		return save();
	}

	public boolean clear() {
		ItemStack[] oldContents = cloneContents(bp.getContents());

		bp.clear();
		setChanged();

		if (!save()) {
			bp.setContents(oldContents);
			return false;
		}

		return true;
	}

	private static ItemStack[] cloneContents(ItemStack[] input) {
		ItemStack[] copy = new ItemStack[input.length];

		for (int i = 0; i < input.length; i++) {
			copy[i] = input[i] == null ? null : input[i].clone();
		}

		return copy;
	}

	public boolean hasOtherEditableViewer(Player player) {
		for (Map.Entry<Player, Boolean> entry : opened.entrySet()) {
			if (Boolean.TRUE.equals(entry.getValue()) && !entry.getKey().equals(player)) {
				return true;
			}
		}

		return false;
	}

	public boolean handleClose(Player player, Inventory closedInventory, boolean storageHealthy) {
		Boolean editableValue = opened.remove(player);
		boolean editable = Boolean.TRUE.equals(editableValue);

		OpenSnapshot snapshot = snapshots.remove(player.getUniqueId());

		if (!editable) {
			return true;
		}

		if (!storageHealthy) {
			rollback(player, snapshot);
			return false;
		}

		ItemStack[] previousBackpackContents = cloneContents(bp.getContents());

		bp.setContents(cloneContents(closedInventory.getContents()));
		setChanged();

		boolean saved = save();

		if (saved) {
			return true;
		}

		if (snapshot != null) {
			rollback(player, snapshot);
		} else {
			bp.setContents(previousBackpackContents);
		}

		Minepacks.getInstance().markStorageFailure(
				new IllegalStateException("Backpack save failed on inventory close.")
		);

		return false;
	}

	private void rollback(Player player, OpenSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}

		bp.setContents(cloneContents(snapshot.backpackContents));

		player.getInventory().setStorageContents(cloneContents(snapshot.storageContents));
		player.getInventory().setArmorContents(cloneContents(snapshot.armorContents));
		player.getInventory().setItemInOffHand(
				snapshot.offHand == null ? null : snapshot.offHand.clone()
		);

		player.setItemOnCursor(
				snapshot.cursorItem == null ? null : snapshot.cursorItem.clone()
		);

		player.updateInventory();
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
		if (inventory.getHolder() instanceof Backpack backpack) {
			return backpack;
		}

		return inventoryBackpackMap.get(inventory);
	}

	// Method to unregister an Inventory when closed
	public static void unregisterInventory(Inventory inventory) {
		inventoryBackpackMap.remove(inventory);
	}
}
