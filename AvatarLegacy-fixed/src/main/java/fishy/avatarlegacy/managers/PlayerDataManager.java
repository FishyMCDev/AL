package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final AvatarLegacy plugin;
    private final Map<UUID, PlayerData> playerDataCache;

    public PlayerDataManager(AvatarLegacy plugin) {
        this.plugin = plugin;
        this.playerDataCache = new ConcurrentHashMap<>();
    }

    public PlayerData getPlayerData(UUID uuid) {
        if (playerDataCache.containsKey(uuid)) return playerDataCache.get(uuid);
        return loadPlayerData(uuid);
    }

    public PlayerData loadPlayerData(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "SELECT * FROM players WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                PlayerData data = new PlayerData(uuid, rs.getString("username"));
                data.setTutorialCompleted(rs.getBoolean("tutorial_completed"));
                data.setElement(rs.getString("element"));
                data.setElementSelectionTimestamp(rs.getLong("element_selection_timestamp"));
                data.setElementPermanent(rs.getBoolean("element_permanent"));
                data.setPlaytimeSeconds(rs.getLong("playtime_seconds"));
                data.setCustomXP(rs.getInt("custom_xp"));
                data.setLastLogin(rs.getLong("last_login"));
                data.setVisitedFireTerritory(rs.getBoolean("visited_fire_territory"));
                data.setVisitedWaterTerritory(rs.getBoolean("visited_water_territory"));
                data.setVisitedEarthTerritory(rs.getBoolean("visited_earth_territory"));
                data.setVisitedAirTerritory(rs.getBoolean("visited_air_territory"));
                data.setLastActivityTime(rs.getLong("last_activity_time"));
                data.setRefugee(rs.getBoolean("is_refugee"));
                data.setHasEverChosen(rs.getBoolean("has_ever_chosen"));

                String bedWorld = rs.getString("bed_spawn_world");
                if (bedWorld != null) {
                    Location bedSpawn = new Location(
                            plugin.getServer().getWorld(bedWorld),
                            rs.getDouble("bed_spawn_x"),
                            rs.getDouble("bed_spawn_y"),
                            rs.getDouble("bed_spawn_z")
                    );
                    data.setBedSpawn(bedSpawn);
                }

                playerDataCache.put(uuid, data);
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player data for " + uuid + ": " + e.getMessage());
        }

        return null;
    }

    public void createPlayerData(Player player) {
        PlayerData data = new PlayerData(player.getUniqueId(), player.getName());
        playerDataCache.put(player.getUniqueId(), data);
        savePlayerData(data);
    }

    public void savePlayerData(PlayerData data) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "INSERT OR REPLACE INTO players (uuid, username, tutorial_completed, element, " +
                "element_selection_timestamp, element_permanent, playtime_seconds, custom_xp, last_login, " +
                "visited_fire_territory, visited_water_territory, visited_earth_territory, visited_air_territory, " +
                "last_activity_time, bed_spawn_world, bed_spawn_x, bed_spawn_y, bed_spawn_z, is_refugee, has_ever_chosen) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, data.getUuid().toString());
            stmt.setString(2, data.getUsername());
            stmt.setBoolean(3, data.isTutorialCompleted());
            stmt.setString(4, data.getElement());
            stmt.setLong(5, data.getElementSelectionTimestamp());
            stmt.setBoolean(6, data.isElementPermanent());
            stmt.setLong(7, data.getPlaytimeSeconds());
            stmt.setInt(8, data.getCustomXP());
            stmt.setLong(9, data.getLastLogin());
            stmt.setBoolean(10, data.hasVisitedFireTerritory());
            stmt.setBoolean(11, data.hasVisitedWaterTerritory());
            stmt.setBoolean(12, data.hasVisitedEarthTerritory());
            stmt.setBoolean(13, data.hasVisitedAirTerritory());
            stmt.setLong(14, data.getLastActivityTime());

            Location bedSpawn = data.getBedSpawn();
            if (bedSpawn != null && bedSpawn.getWorld() != null) {
                stmt.setString(15, bedSpawn.getWorld().getName());
                stmt.setDouble(16, bedSpawn.getX());
                stmt.setDouble(17, bedSpawn.getY());
                stmt.setDouble(18, bedSpawn.getZ());
            } else {
                stmt.setNull(15, java.sql.Types.VARCHAR);
                stmt.setNull(16, java.sql.Types.DOUBLE);
                stmt.setNull(17, java.sql.Types.DOUBLE);
                stmt.setNull(18, java.sql.Types.DOUBLE);
            }

            stmt.setBoolean(19, data.isRefugee());
            stmt.setBoolean(20, data.hasEverChosen());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save player data for " + data.getUuid() + ": " + e.getMessage());
        }
    }

    
    public void removeFromCache(UUID uuid) {
        playerDataCache.remove(uuid);
    }

    public void unloadPlayerData(UUID uuid) {
        PlayerData data = playerDataCache.get(uuid);
        if (data != null) {
            savePlayerData(data);
            playerDataCache.remove(uuid);
        }
    }

    public void saveAllPlayerData() {
        for (PlayerData data : playerDataCache.values()) {
            savePlayerData(data);
        }
    }
}
