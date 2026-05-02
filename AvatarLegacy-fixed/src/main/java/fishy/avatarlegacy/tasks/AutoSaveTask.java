package fishy.avatarlegacy.tasks;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.scheduler.BukkitRunnable;

public class AutoSaveTask extends BukkitRunnable {
    private final AvatarLegacy plugin;

    public AutoSaveTask(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getPlayerDataManager().saveAllPlayerData();
    }
}