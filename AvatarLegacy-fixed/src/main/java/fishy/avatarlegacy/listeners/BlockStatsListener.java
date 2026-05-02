package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockStatsListener implements Listener {
    private final AvatarLegacy plugin;

    public BlockStatsListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) == null) return;
        plugin.getStatsManager().incrementBlocksBroken(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) == null) return;
        plugin.getStatsManager().incrementBlocksPlaced(player.getUniqueId());
    }
}

