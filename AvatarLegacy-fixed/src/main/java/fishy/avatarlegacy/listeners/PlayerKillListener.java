package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerKillListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerKillListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getPlayer().getKiller();
        if (killer == null) return;
        if (killer.equals(event.getPlayer())) return;
        plugin.getStatsManager().incrementKillCount(killer.getUniqueId());
        plugin.getAvatarManager().updateCandidateScoreForPlayer(killer.getUniqueId());
    }
}
