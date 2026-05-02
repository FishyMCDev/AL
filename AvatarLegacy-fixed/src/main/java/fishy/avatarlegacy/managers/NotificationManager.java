package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationManager {

    private final AvatarLegacy plugin;

    public NotificationManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public void queueNotification(UUID uuid, String message) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "INSERT INTO offline_notifications (uuid, message, timestamp) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, message);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to queue notification: " + e.getMessage());
        }
    }

    public void queueNotificationForNation(int nationId, String message) {
        List<UUID> citizens = plugin.getNationManager().getNationCitizens(nationId);
        for (UUID citizen : citizens) {
            queueNotification(citizen, message);
        }
    }

    public void sendOfflineNotifications(Player player) {
        List<String> notifications = getOfflineNotifications(player.getUniqueId());

        if (notifications.isEmpty()) return;

        player.sendMessage(MessageUtil.prefix("Notifications", "You have " + notifications.size() + " notification(s):"));

        for (String notification : notifications) {
            player.sendMessage(MessageUtil.info("- " + notification));
        }

        clearOfflineNotifications(player.getUniqueId());
    }

    private List<String> getOfflineNotifications(UUID uuid) {
        List<String> notifications = new ArrayList<>();
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "SELECT message FROM offline_notifications WHERE uuid = ? ORDER BY timestamp ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                notifications.add(rs.getString("message"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get offline notifications: " + e.getMessage());
        }

        return notifications;
    }

    private void clearOfflineNotifications(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "DELETE FROM offline_notifications WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear offline notifications: " + e.getMessage());
        }
    }
}