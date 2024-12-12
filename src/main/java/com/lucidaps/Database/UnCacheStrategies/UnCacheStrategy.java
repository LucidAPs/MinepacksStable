package com.lucidaps.Database.UnCacheStrategies;

import com.lucidaps.Database.Database;
import com.lucidaps.Minepacks;

public abstract class UnCacheStrategy {
	protected Database cache;

	protected UnCacheStrategy(Database cache) {
		this.cache = cache;
	}

	public static UnCacheStrategy getUnCacheStrategy(Database cache) {
		// Get the strategy from the config
		String strategy = Minepacks.getInstance().getConfig().getString("Database.Cache.UnCache.Strategy", "interval");
		switch(strategy.toLowerCase()) {
			case "ondisconnect": return new OnDisconnect(cache);
			case "ondisconnectdelayed": return new OnDisconnectDelayed(cache);
			case "intervalchecked": return new IntervalChecked(cache);
			case "interval":
			default: return new Interval(cache);
		}
	}

	public void close() {
		cache = null;
	}
}
