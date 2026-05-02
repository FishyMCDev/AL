package fishy.avatarlegacy.listeners;

import com.projectkorra.projectkorra.event.PlayerBindChangeEvent;
import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

public class PlayerBindAbilityListener implements Listener {

    private final AvatarLegacy plugin;

    public PlayerBindAbilityListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBind(PlayerBindChangeEvent event) {
        if (!event.isBinding()) return;

        
        org.bukkit.OfflinePlayer offlinePlayer = event.getPlayer();
        if (offlinePlayer == null || !offlinePlayer.isOnline()) return;
        Player player = offlinePlayer.getPlayer();
        if (player == null) return;

        
        if (plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) == null) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You must create a character before binding abilities!"));
            return;
        }

        String abilityName = event.getAbility();
        if (abilityName == null) return;

        
        List<String> removed = plugin.getStatsManager().getRemovedMoves(player.getUniqueId());
        for (String move : removed) {
            if (move.equalsIgnoreCase(abilityName)) {
                event.setCancelled(true);
                player.sendMessage(MessageUtil.error("§e" + abilityName
                        + "§c has been removed! Restore it via §e/stats restore§c."));
                return;
            }
        }

        
        List<String> Protected = plugin.getProtectedMovesManager().getProtectedMoves(player.getUniqueId());
        for (String move : Protected) {
            if (move.equalsIgnoreCase(abilityName)) return;
        }

        
        if (plugin.getAvatarManager().isAvatar(player.getUniqueId())) return;

        
    }
}
