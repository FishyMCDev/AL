package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;
import java.util.UUID;

public class BlockBreakListener implements Listener {
    private final AvatarLegacy plugin;

    public BlockBreakListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("avatarlegacy.admin")) return;

        Integer chunkOwner = plugin.getWorldGuardIntegration().getChunkOwner(block.getChunk());
        if (chunkOwner == null) return;

        Integer playerNation = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (playerNation != null && playerNation.equals(chunkOwner)) {
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
        player.sendMessage(MessageUtil.error("You cannot place blocks in another nation\'s claimed territory!"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        Integer nationId = plugin.getNationManager().getNationByCore(block.getLocation());

        if (nationId != null && block.getType() == Material.BEACON) {
            handleCoreBreak(event, player, block, nationId);
            return;
        }

        if (block.getType() == Material.BEACON) return;

        Integer chunkOwner = plugin.getWorldGuardIntegration().getChunkOwner(block.getChunk());
        if (chunkOwner == null) return;

        if (player.isOp() || player.hasPermission("avatarlegacy.admin")) return;

        Integer playerNation = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (playerNation != null && playerNation.equals(chunkOwner)) {
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
        player.sendMessage(MessageUtil.error("You cannot break blocks in another nation\'s claimed territory!"));
    }

    private void handleCoreBreak(BlockBreakEvent event, Player player, Block block, int nationId) {
        event.setCancelled(true);

        if (plugin.getNationManager().isInGracePeriod(nationId)) {
            player.sendMessage(MessageUtil.error("This nation is in its grace period and cannot be attacked!"));
            return;
        }

        if (!player.isOp() && !player.hasPermission("avatarlegacy.admin")) {
            Integer breakerNation = plugin.getNationManager().getPlayerNation(player.getUniqueId());
            if (breakerNation == null) {
                player.sendMessage(MessageUtil.error("You must be in a nation to attack a nation core!"));
                return;
            }
            if (!plugin.getWarManager().isAtWar(breakerNation, nationId)) {
                player.sendMessage(MessageUtil.error("You must be at war with this nation to attack their core!"));
                return;
            }
        }

        if (plugin.getNationManager().isShieldActive(nationId)) {
            long remainingMs = plugin.getNationManager().getShieldRemainingMs(nationId);
            long mins = remainingMs / 60000;
            player.sendMessage(MessageUtil.error("This core is shielded! Shield expires in " + mins + " minute(s)."));
            return;
        }

        int maxDurability = plugin.getConfig().getInt("core.durability", 20);
        int hits = plugin.getNationManager().incrementCoreHits(nationId);
        String nationName = plugin.getNationManager().getNationName(nationId);

        List<UUID> citizens = plugin.getNationManager().getNationCitizens(nationId);
        for (UUID citizen : citizens) {
            Player cp = Bukkit.getPlayer(citizen);
            String msg = "\u00a7c\u00a7l\u26a0 Your nation core is under attack! [" + hits + "/" + maxDurability + "]";
            if (cp != null) cp.sendMessage(msg);
            else plugin.getNotificationManager().queueNotification(citizen,
                    "Your nation core was attacked while you were offline! [" + hits + "/" + maxDurability + "]");
        }

        player.sendMessage(MessageUtil.warning("Nation core hit! [" + hits + "/" + maxDurability + "]"));

        if (hits >= maxDurability) {
            event.setCancelled(false);
            plugin.getNationManager().resetCoreHits(nationId);

            for (UUID citizen : citizens) {
                Player cp = Bukkit.getPlayer(citizen);
                if (cp != null) cp.sendMessage(MessageUtil.error("\u00a74\u00a7l\u2620 YOUR NATION CORE HAS BEEN DESTROYED!"));
                else plugin.getNotificationManager().queueNotification(citizen, "Your nation was destroyed while you were offline!");
            }

            plugin.getNationManager().disbandNation(nationId);
            Bukkit.broadcast(MessageUtil.warning("Nation " + nationName + " has been DESTROYED! Their core was broken by " + player.getName()));
        }
    }
}
