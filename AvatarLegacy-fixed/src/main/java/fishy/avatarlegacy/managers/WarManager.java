package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WarManager {

    private final AvatarLegacy plugin;

    public WarManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public boolean canDeclareWar(int attackerNationId, int defenderNationId) {
        if (plugin.getNationManager().isInGracePeriod(defenderNationId)) return false;
        if (isAtWar(attackerNationId, defenderNationId)) return false;
        return true;
    }

    public int declareWar(int attackerNationId, int defenderNationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO nation_wars (attacker_nation_id, defender_nation_id, start_timestamp, status) VALUES (?, ?, ?, 'active')",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, attackerNationId);
            stmt.setInt(2, defenderNationId);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int warId = rs.getInt(1);

                String attackerName = plugin.getNationManager().getNationName(attackerNationId);
                String defenderName = plugin.getNationManager().getNationName(defenderNationId);

                Bukkit.broadcast(MessageUtil.warBroadcast(attackerName, defenderName));

                notifyOfflineCitizens(attackerNationId,
                        "While you were offline: Your nation declared war on " + defenderName + "!");
                notifyOfflineCitizens(defenderNationId,
                        "While you were offline: " + attackerName + " declared war on your nation!");

                return warId;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to declare war: " + e.getMessage());
        }
        return -1;
    }

    public void endWar(int warId, String endStatus) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE nation_wars SET status = ?, end_timestamp = ? WHERE id = ?")) {
            stmt.setString(1, endStatus);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setInt(3, warId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to end war: " + e.getMessage());
        }
    }

    public int getActiveWarId(int nationId1, int nationId2) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "SELECT id FROM nation_wars WHERE status = 'active' AND " +
                "((attacker_nation_id = ? AND defender_nation_id = ?) OR " +
                "(attacker_nation_id = ? AND defender_nation_id = ?))";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, nationId1);
            stmt.setInt(2, nationId2);
            stmt.setInt(3, nationId2);
            stmt.setInt(4, nationId1);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active war id: " + e.getMessage());
        }
        return -1;
    }

    public boolean isAtWar(int nationId1, int nationId2) {
        return getActiveWarId(nationId1, nationId2) != -1;
    }

    public List<Integer> getEnemyNations(int nationId) {
        List<Integer> enemies = new ArrayList<>();
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT attacker_nation_id, defender_nation_id FROM nation_wars WHERE status = 'active' AND " +
                "(attacker_nation_id = ? OR defender_nation_id = ?)")) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, nationId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int attacker = rs.getInt("attacker_nation_id");
                int defender = rs.getInt("defender_nation_id");
                enemies.add(attacker == nationId ? defender : attacker);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get enemy nations: " + e.getMessage());
        }
        return enemies;
    }

    public List<UUID> getEnemyPlayers(int nationId) {
        List<UUID> enemies = new ArrayList<>();
        for (int enemyNationId : getEnemyNations(nationId)) {
            enemies.addAll(plugin.getNationManager().getNationCitizens(enemyNationId));
        }
        return enemies;
    }

    private void notifyOfflineCitizens(int nationId, String message) {
        for (UUID uuid : plugin.getNationManager().getNationCitizens(nationId)) {
            if (Bukkit.getPlayer(uuid) == null) {
                plugin.getNotificationManager().queueNotification(uuid, message);
            }
        }
    }
}
