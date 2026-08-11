package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class PlayerConsumeListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerConsumeListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) == null) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "eat", 1);
    }
}
