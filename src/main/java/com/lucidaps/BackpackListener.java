package com.lucidaps;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashMap;
import java.util.Map;

public class BackpackListener implements Listener {
    private final Minepacks plugin;

    public BackpackListener(Minepacks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Backpack backpack = Backpack.fromInventory(event.getInventory());
        if (backpack == null) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            Backpack.unregisterInventory(event.getInventory());
            return;
        }

        if (plugin.areSoundsEnabled() && plugin.getCloseSound() != null) {
            player.playSound(
                    player.getLocation(),
                    plugin.getCloseSound(),
                    plugin.getSoundVolume(),
                    plugin.getSoundPitch()
            );
        }

        boolean saved = backpack.handleClose(
                player,
                event.getInventory(),
                plugin.isStorageHealthy()
        );

        Backpack.unregisterInventory(event.getInventory());

        if (!saved) {
            player.sendMessage("§cBackpack changes were not saved because server storage is unavailable.");
            player.sendMessage("§cYour inventory was rolled back to prevent item duplication.");
            return;
        }

        boolean isOwner = backpack.getOwner().getUniqueId().equals(player.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        if (!isOwner) {
            String ownerName = backpack.getOwner().getName();
            if (ownerName == null) ownerName = "Unknown";
            placeholders.put("OwnerName", ownerName);
        }

        String messagePath = isOwner
                ? "Language.Ingame.OwnBackpackClose"
                : "Language.Ingame.PlayerBackpackClose";

        plugin.sendFormattedMessage(player, messagePath, placeholders);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Backpack backpack = Backpack.fromInventory(event.getInventory());
        if (backpack == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        if (!backpack.canEdit(player)) {
            event.setCancelled(true);
        } else {
            backpack.setChanged();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Backpack backpack = Backpack.fromInventory(event.getInventory());
        if (backpack == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        if (!backpack.canEdit(player)) {
            event.setCancelled(true);
        } else {
            backpack.setChanged();
        }
    }
}