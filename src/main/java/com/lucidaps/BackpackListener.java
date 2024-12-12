package com.lucidaps;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;

public class BackpackListener implements Listener {
    private final Minepacks plugin;

    public BackpackListener(Minepacks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Retrieve the Backpack instance from the closed inventory
        Backpack backpack = Backpack.fromInventory(event.getInventory());
        if (backpack == null) {
            return; // Not a backpack inventory
        }

        Player player = (Player) event.getPlayer();

        // Play the closing sound if enabled and configured
        if (plugin.areSoundsEnabled() && plugin.getCloseSound() != null) {
            player.playSound(player.getLocation(), plugin.getCloseSound(), plugin.getSoundVolume(), plugin.getSoundPitch());
        }

        // Determine if the player is the owner
        boolean isOwner = backpack.getOwner().getUniqueId().equals(player.getUniqueId());

        // Prepare placeholders
        Map<String, String> placeholders = new HashMap<>();
        if (!isOwner) {
            String ownerName = backpack.getOwner().getName();
            if (ownerName == null) ownerName = "Unknown";
            placeholders.put("OwnerName", ownerName);
        }

        // Determine the message path based on ownership
        String messagePath = isOwner ? "Language.Ingame.OwnBackpackClose" : "Language.Ingame.PlayerBackpackClose";

        // Send the message using the utility method
        plugin.sendFormattedMessage(player, messagePath, placeholders);

        // Unregister the inventory to clean up
        Backpack.unregisterInventory(event.getInventory());
    }
}
