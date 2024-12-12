package com.lucidaps.Database.UnCacheStrategies;

import com.lucidaps.Backpack;
import com.lucidaps.Database.Database;
import com.lucidaps.Minepacks;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class OnDisconnect extends UnCacheStrategy implements Listener {
	public OnDisconnect(Database cache) {
		super(cache);
		Bukkit.getPluginManager().registerEvents(this, Minepacks.getInstance());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void playerLeaveEvent(PlayerQuitEvent event) {
		Backpack backpack = cache.getBackpack(event.getPlayer());
		if(backpack != null && !backpack.isOpen()) {
			cache.unloadBackpack(backpack);
		}
	}

	@Override
	public void close() {
		HandlerList.unregisterAll(this);
	}
}
