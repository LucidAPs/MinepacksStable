package com.lucidaps.Database.UnCacheStrategies;

import com.lucidaps.Backpack;
import com.lucidaps.Database.Database;
import com.lucidaps.Minepacks;
import org.bukkit.Bukkit;

public class Interval extends UnCacheStrategy implements Runnable {
	private final int taskID;

	public Interval(Database cache) {
		super(cache);
		int delay = Minepacks.getInstance().getConfig().getInt("Database.Cache.UnCache.Delay", 600);
		int interval = Minepacks.getInstance().getConfig().getInt("Database.Cache.UnCache.Interval", 600);
		taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(Minepacks.getInstance(), this, delay * 20L, interval * 20L);
	}

	@Override
	public void run() {
		for(Backpack backpack : cache.getLoadedBackpacks()) {
			if(backpack.getOwnerPlayer() == null && !backpack.isOpen()) {
				this.cache.unloadBackpack(backpack);
			}
		}
	}

	@Override
	public void close() {
		Bukkit.getScheduler().cancelTask(taskID);
		super.close();
	}
}
