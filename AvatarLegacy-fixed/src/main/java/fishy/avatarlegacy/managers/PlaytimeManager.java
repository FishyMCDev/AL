package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlaytimeManager {

    private final AvatarLegacy plugin;

    public PlaytimeManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public void startSession(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data != null) data.updateActivity();
    }

    public void endSession(Player player) {
        
    }

    public void updatePlaytime(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        long afkMinutes = plugin.getConfig().getLong("afk.kick-after-minutes", 0);
        
        boolean isAfk = (afkMinutes > 0) && data.isAFK(afkMinutes * 60L * 1000L);

        if (!isAfk) {
            long previousSeconds = data.getPlaytimeSeconds();
            data.addPlaytimeSeconds(1);

            int intervalMinutes = plugin.getConfig().getInt("custom-xp.playtime-interval-minutes", 30);
            long intervalSeconds = intervalMinutes * 60L;

            long previousIntervals = previousSeconds / intervalSeconds;
            long currentIntervals  = data.getPlaytimeSeconds() / intervalSeconds;

            if (currentIntervals > previousIntervals) {
                plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "playtime", 1);
            }
        }
    }

    public long getPlaytimeHours(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        return data != null ? data.getPlaytimeSeconds() / 3600 : 0;
    }

    public long getPlaytimeMinutes(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        return data != null ? data.getPlaytimeSeconds() / 60 : 0;
    }
}
