package fishy.avatarlegacy.tasks;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class TaxCollectionTask extends BukkitRunnable {
    private final AvatarLegacy plugin;
    private boolean hasCollectedToday = false;

    public TaxCollectionTask(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String collectionTime = plugin.getConfig().getString("taxes.collection-time", "00:00");
        LocalTime now = LocalTime.now();
        LocalTime target = LocalTime.parse(collectionTime);

        if (now.getHour() == target.getHour() && now.getMinute() == target.getMinute()) {
            if (!hasCollectedToday) {
                collectTaxes();
                hasCollectedToday = true;
            }
        } else {
            hasCollectedToday = false;
        }
    }

    private void collectTaxes() {

        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "SELECT id, name, tax_rate FROM nations WHERE tax_rate > 0";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int nationId = rs.getInt("id");
                String nationName = rs.getString("name");
                double taxRate = rs.getDouble("tax_rate");

                List<UUID> citizens = plugin.getNationManager().getNationCitizens(nationId);
                double totalCollected = 0;

                for (UUID citizenUuid : citizens) {
                    double balance = plugin.getEconomyManager().getBalance(citizenUuid);
                    double taxAmount = balance * (taxRate / 100.0);

                    if (taxAmount > 0) {
                        if (plugin.getEconomyManager().withdraw(citizenUuid, taxAmount)) {
                            plugin.getNationManager().depositTreasury(nationId, taxAmount);
                            totalCollected += taxAmount;

                            org.bukkit.entity.Player online = Bukkit.getPlayer(citizenUuid);
                            String msg = "§6[Tax] §e¥" + String.format("%.2f", taxAmount)
                                    + " §6was collected as nation tax §7(" + taxRate + "%) §6by §e" + nationName + "§6.";
                            if (online != null) {
                                online.sendMessage(msg);
                            } else {
                                plugin.getNotificationManager().queueNotification(citizenUuid,
                                        "While you were offline: ¥" + String.format("%.2f", taxAmount) + " was collected as nation tax (" + taxRate + "%) by " + nationName);
                            }
                        } else {
                            org.bukkit.entity.Player online = Bukkit.getPlayer(citizenUuid);
                            String failMsg = "§c[Tax] §7Nation §e" + nationName + " §7tried to collect tax but your balance was insufficient.";
                            if (online != null) {
                                online.sendMessage(failMsg);
                            } else {
                                plugin.getNotificationManager().queueNotification(citizenUuid,
                                        "While you were offline: Failed to collect nation tax from " + nationName + " (insufficient funds)");
                            }
                        }
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to collect taxes: " + e.getMessage());
        }
    }
}
