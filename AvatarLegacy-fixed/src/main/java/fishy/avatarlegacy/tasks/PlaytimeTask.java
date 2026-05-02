package fishy.avatarlegacy.tasks;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PlaytimeTask extends BukkitRunnable {
    private final AvatarLegacy plugin;
    private int tickCounter = 0;

    public PlaytimeTask(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCounter++;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.getPlaytimeManager().updatePlaytime(player);
            plugin.getElementManager().checkAndMakePermanent(player);
        }

        if (tickCounter >= 3600) tickCounter = 0;
    }
}
