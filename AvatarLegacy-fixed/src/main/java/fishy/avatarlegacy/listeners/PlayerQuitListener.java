package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerQuitListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPlaytimeManager().endSession(player);
        plugin.getSkillTreeManager().unloadPlayerData(player.getUniqueId());
        plugin.getPlayerDataManager().unloadPlayerData(player.getUniqueId());
        plugin.getElementManager().evictPlaytimeCache(player.getUniqueId());
        plugin.getTeleportManager().evict(player.getUniqueId());
        plugin.getSkyFreezeManager().evict(player.getUniqueId());
    }
}
