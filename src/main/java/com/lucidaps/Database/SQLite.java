package com.lucidaps.Database;

import com.lucidaps.Backpack;
import com.lucidaps.Minepacks;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class SQLite extends Database {
	private final String dbFile;

	public SQLite(Minepacks plugin) {
		super(plugin);
		dbFile = plugin.getDataFolder().getAbsolutePath() + File.separator + "backpack.db";
		initDB();
	}

	private Connection getConnection() throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:" + dbFile);
	}

	private void initDB() {
		try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("CREATE TABLE IF NOT EXISTS backpack_players (player_id INTEGER PRIMARY KEY AUTOINCREMENT, name CHAR(16), uuid CHAR(32) UNIQUE);");
			stmt.execute("CREATE TABLE IF NOT EXISTS backpacks (owner INT, itemstacks BLOB, version INT, lastupdate DATE, PRIMARY KEY(owner));");
		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to initialize the database!", e);
		}
	}

	@Override
	public void updatePlayer(Player player) {
		try (Connection conn = getConnection()) {
			String uuidStr = player.getUniqueId().toString().replace("-", "");
			try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO backpack_players (name, uuid) VALUES (?,?)")) {
				ps.setString(1, player.getName());
				ps.setString(2, uuidStr);
				ps.execute();
			}

			try (PreparedStatement ps = conn.prepareStatement("UPDATE backpack_players SET name=? WHERE uuid=?")) {
				ps.setString(1, player.getName());
				ps.setString(2, uuidStr);
				ps.execute();
			}
		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to update player in DB!", e);
		}
	}

	@Override
	public void saveBackpack(Backpack backpack) {
		try (Connection conn = getConnection()) {
			int playerId = backpack.getOwnerDatabaseId();
			if (playerId <= 0) {
				playerId = getPlayerId(conn, backpack.getOwner().getUniqueId());
				if (playerId <= 0) {
					Player ownerPlayer = backpack.getOwnerPlayer();
					if (ownerPlayer != null) updatePlayer(ownerPlayer);
					playerId = getPlayerId(conn, backpack.getOwner().getUniqueId());
					if (playerId <= 0) {
						plugin.getLogger().warning("Could not get player ID for " + backpack.getOwner().getName() + "!");
						return; // fallback if no player id
					}
				}
				backpack.setOwnerDatabaseId(playerId);
			}

			// Serialize items
			ItemStack[] contents = backpack.getInventory().getContents();
			byte[] data = ItemStackArraySerializer.serializeItemStacks(contents);
			int version = 1;

			try (PreparedStatement ps = conn.prepareStatement("REPLACE INTO backpacks (owner, itemstacks, version, lastupdate) VALUES (?,?,?,DATE('now'))")) {
				ps.setInt(1, playerId);
				ps.setBytes(2, data);
				ps.setInt(3, version);
				ps.execute();
			}

		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to save backpack!", e);
		}
	}

	@Override
	protected Backpack loadBackpack(OfflinePlayer player) {
		try (Connection conn = getConnection()) {
			int playerId = getPlayerId(conn, player.getUniqueId());
			if (playerId <= 0) return null;

			try (PreparedStatement ps = conn.prepareStatement("SELECT itemstacks, version FROM backpacks WHERE owner=?")) {
				ps.setInt(1, playerId);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						byte[] data = rs.getBytes("itemstacks");
						int version = rs.getInt("version");
						ItemStack[] items = ItemStackArraySerializer.deserializeItemStacks(data);
						return new Backpack(player, (items != null ? items : new ItemStack[9]), playerId);
					}
				}
			}
		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to load backpack!", e);
		}
		return null;
	}

	private int getPlayerId(Connection conn, UUID uuid) throws SQLException {
		String uuidStr = uuid.toString().replace("-", "");
		try (PreparedStatement ps = conn.prepareStatement("SELECT player_id FROM backpack_players WHERE uuid=?")) {
			ps.setString(1, uuidStr);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("player_id");
				}
			}
		}
		return -1;
	}
}
