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
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);

		try (Statement stmt = conn.createStatement()) {
			stmt.execute("PRAGMA busy_timeout = 5000;");
			stmt.execute("PRAGMA foreign_keys = ON;");
			stmt.execute("PRAGMA synchronous = FULL;");
		}

		return conn;
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
	public boolean saveBackpack(Backpack backpack) {
		try (Connection conn = getConnection()) {
			conn.setAutoCommit(false);

			try {
				int playerId = backpack.getOwnerDatabaseId();

				if (playerId <= 0) {
					playerId = ensurePlayerId(conn, backpack.getOwner());
					backpack.setOwnerDatabaseId(playerId);
				}

				ItemStack[] contents = backpack.getInventory().getContents();
				byte[] data = ItemStackArraySerializer.serializeItemStacks(contents);

				try (PreparedStatement ps = conn.prepareStatement(
						"""
                        INSERT INTO backpacks (owner, itemstacks, version, lastupdate)
                        VALUES (?, ?, 1, datetime('now'))
                        ON CONFLICT(owner) DO UPDATE SET
                            itemstacks = excluded.itemstacks,
                            version = backpacks.version + 1,
                            lastupdate = datetime('now')
                        """
				)) {
					ps.setInt(1, playerId);
					ps.setBytes(2, data);
					ps.executeUpdate();
				}

				conn.commit();
				return true;

			} catch (SQLException | RuntimeException e) {
				try {
					conn.rollback();
				} catch (SQLException rollbackException) {
					plugin.getLogger().log(Level.SEVERE, "Failed to rollback backpack save!", rollbackException);
				}

				throw e;
			}

		} catch (SQLException | RuntimeException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to save backpack!", e);

			if (isStorageFailure(e)) {
				plugin.markStorageFailure(e);
			}

			return false;
		}
	}

	private int ensurePlayerId(Connection conn, OfflinePlayer player) throws SQLException {
		String uuidStr = player.getUniqueId().toString().replace("-", "");
		String name = player.getName();

		if (name == null || name.isBlank()) {
			name = "Unknown";
		}

		try (PreparedStatement ps = conn.prepareStatement(
				"INSERT OR IGNORE INTO backpack_players (name, uuid) VALUES (?, ?)"
		)) {
			ps.setString(1, name);
			ps.setString(2, uuidStr);
			ps.executeUpdate();
		}

		try (PreparedStatement ps = conn.prepareStatement(
				"UPDATE backpack_players SET name = ? WHERE uuid = ?"
		)) {
			ps.setString(1, name);
			ps.setString(2, uuidStr);
			ps.executeUpdate();
		}

		int playerId = getPlayerId(conn, player.getUniqueId());

		if (playerId <= 0) {
			throw new SQLException("Could not create or find player ID for " + player.getUniqueId());
		}

		return playerId;
	}

	private boolean isStorageFailure(Throwable throwable) {
		Throwable current = throwable;

		while (current != null) {
			String message = current.getMessage();

			if (message != null) {
				String lower = message.toLowerCase(java.util.Locale.ROOT);

				if (
						lower.contains("database or disk is full") ||
								lower.contains("sqlite_full") ||
								lower.contains("disk i/o error") ||
								lower.contains("readonly database") ||
								lower.contains("no space left on device")
				) {
					return true;
				}
			}

			current = current.getCause();
		}

		return false;
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
		} catch (SQLException | RuntimeException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to load backpack!", e);
			plugin.markStorageFailure(e);
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
