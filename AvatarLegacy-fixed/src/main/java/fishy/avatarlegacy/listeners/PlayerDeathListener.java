package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.LocationUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerDeathListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        event.setDeathMessage(null);

        String cause = player.getLastDamageCause() != null ?
                player.getLastDamageCause().getCause().name() : "UNKNOWN";
        String location = LocationUtil.serialize(player.getLocation());

        plugin.getStatsManager().handleDeath(player, cause, location);

        if (plugin.getAvatarManager().isAvatar(player.getUniqueId())) {
            plugin.getAvatarManager().handleAvatarDeath(player);
        }
    }
}
