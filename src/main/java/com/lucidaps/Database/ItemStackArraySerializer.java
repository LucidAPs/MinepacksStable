package com.lucidaps.Database;

import com.lucidaps.Minepacks;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.List;

public class ItemStackArraySerializer {
    public static byte[] serializeItemStacks(ItemStack[] items) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items", items);
        String data = config.saveToString();
        return Base64.getEncoder().encode(data.getBytes());
    }

    public static ItemStack[] deserializeItemStacks(byte[] data) {
        if (data == null || data.length == 0) return new ItemStack[9]; // Default empty inventory

        try {
            String text = new String(Base64.getDecoder().decode(data));
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(text);
            return ((List<ItemStack>) config.get("items", new ItemStack[0])).toArray(new ItemStack[0]);
        } catch (Exception e) {
            Minepacks.getInstance().getLogger().severe("Deserialization failed: " + e.getMessage());
            return new ItemStack[9]; // Return empty inventory on failure
        }
    }
}