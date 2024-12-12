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
import org.bukkit.scheduler.BukkitRunnable;

public class OnDisconnectDelayed extends UnCacheStrategy implements Listener {
	private final long delay;

	public OnDisconnectDelayed(Database cache) {
		super(cache);
		int configDelay = Minepacks.getInstance().getConfig().getInt("Database.Cache.UnCache.Delay", 600);
		this.delay = configDelay * 20L; // convert seconds to ticks if needed
		Bukkit.getPluginManager().registerEvents(this, Minepacks.getInstance());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void playerLeaveEvent(PlayerQuitEvent event) {
		final Backpack backpack = cache.getBackpack(event.getPlayer());
		if(backpack != null) {
			new BukkitRunnable() {
				@Override
				public void run() {
					if(!backpack.isOpen()) {
						cache.unloadBackpack(backpack);
					} else {
						this.runTaskLater(Minepacks.getInstance(), delay);
					}
				}
			}.runTaskLater(Minepacks.getInstance(), delay);
		}
	}

	@Override
	public void close() {
		HandlerList.unregisterAll(this);
	}
}
