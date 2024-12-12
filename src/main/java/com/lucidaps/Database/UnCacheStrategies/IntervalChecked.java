package com.lucidaps.Database.UnCacheStrategies;

import com.lucidaps.Backpack;
import com.lucidaps.Database.Database;
import com.lucidaps.Minepacks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class IntervalChecked extends UnCacheStrategy implements Runnable {
	private final long delay;
	private final int taskID;

	public IntervalChecked(Database cache) {
		super(cache);
		int configDelay = Minepacks.getInstance().getConfig().getInt("Database.Cache.UnCache.Delay", 600);
		int configInterval = Minepacks.getInstance().getConfig().getInt("Database.Cache.UnCache.Interval", 600);

		long delayTicks = configDelay * 20L;
		taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(Minepacks.getInstance(), this, delayTicks, configInterval * 20L);
		this.delay = delayTicks * 50L; // delay in ms: 1 tick = 50 ms
	}

	@Override
	public void run() {
		long currentTime = System.currentTimeMillis() - delay;
		for(Backpack backpack : cache.getLoadedBackpacks()) {
			OfflinePlayer owner = backpack.getOwner();
			if(!owner.isOnline() && owner.getLastPlayed() < currentTime && !backpack.isOpen()) {
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
