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
        if (data == null || data.length == 0) {
            return new ItemStack[9];
        }

        try {
            String text = new String(Base64.getDecoder().decode(data));
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(text);

            Object raw = config.get("items");

            if (!(raw instanceof List<?> list)) {
                throw new IllegalStateException("Serialized backpack does not contain an item list.");
            }

            return list.toArray(new ItemStack[0]);

        } catch (Exception e) {
            Minepacks.getInstance().getLogger().severe("Backpack deserialization failed: " + e.getMessage());
            throw new IllegalStateException("Could not deserialize backpack items.", e);
        }
    }
}