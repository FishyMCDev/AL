package fishy.avatarlegacy.tasks;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.scheduler.BukkitRunnable;

public class AFKCheckTask extends BukkitRunnable {
    private final AvatarLegacy plugin;

    public AFKCheckTask(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        
        
        long afkThreshold = plugin.getConfig().getLong("afk.kick-after-minutes", 0);
        if (afkThreshold <= 0) return;

        long thresholdMs = afkThreshold * 60 * 1000;
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            fishy.avatarlegacy.models.PlayerData data =
                    plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (data != null && data.isAFK(thresholdMs)) {
                player.kick(fishy.avatarlegacy.utils.MessageUtil.error("You have been kicked for being AFK!"));
            }
        }
    }
}
