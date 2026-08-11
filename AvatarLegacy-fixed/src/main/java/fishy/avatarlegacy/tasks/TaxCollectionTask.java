package fishy.avatarlegacy.tasks;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
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
        LocalTime target = parseCollectionTime(collectionTime);

        if (now.getHour() == target.getHour() && now.getMinute() == target.getMinute()) {
            if (!hasCollectedToday) {
                collectTaxes();
                hasCollectedToday = true;
            }
        } else {
            hasCollectedToday = false;
        }
    }

    /**
     * Parses "taxes.collection-time" tolerantly. Accepts standard "HH:mm"/"H:mm",
     * and also digit-only values like "720" or "0720" (treated as HHmm), since a
     * missing colon in the config previously caused this task to throw every run
     * and never collect taxes.
     */
    private LocalTime parseCollectionTime(String raw) {
        if (raw == null || raw.isBlank()) return LocalTime.MIDNIGHT;
        String value = raw.trim();
        try {
            return LocalTime.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            // Fall through to digit-only handling below.
        }

        String digits = value.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            if (digits.length() <= 2) digits = digits + "00"; // e.g. "7" -> hour 7, minute 0
            while (digits.length() < 4) digits = "0" + digits;   // left-pad to HHmm
            if (digits.length() > 4) digits = digits.substring(digits.length() - 4);

            try {
                int hour = Integer.parseInt(digits.substring(0, 2));
                int minute = Integer.parseInt(digits.substring(2, 4));
                if (hour <= 23 && minute <= 59) {
                    return LocalTime.of(hour, minute);
                }
            } catch (NumberFormatException | DateTimeException ignored) {
                // fall through to default below
            }
        }

        plugin.getLogger().warning("[AvatarLegacy] Invalid taxes.collection-time '" + raw
                + "', defaulting to 00:00. Expected format: HH:mm (e.g. 07:20).");
        return LocalTime.MIDNIGHT;
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
                UUID leader = plugin.getNationManager().getNationLeader(nationId);
                if (leader != null) {
                    plugin.getStatsManager().awardConfiguredXp(leader, "nation-leader-daily", 1);
                }
                if (citizens.size() >= 3 && leader != null) {
                    plugin.getStatsManager().awardConfiguredXp(leader, "nation-3plus-daily", 1);
                }

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