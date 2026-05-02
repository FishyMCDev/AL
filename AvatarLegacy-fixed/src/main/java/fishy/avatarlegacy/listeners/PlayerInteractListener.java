package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerInteractListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerInteractListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) return;
        if (!plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) return;

        Location coreLoc = plugin.getNationManager().getNationCore(nationId);
        if (coreLoc != null) return;

        if (block.getY() < plugin.getConfig().getInt("core.min-y-level", 62)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("The nation core must be placed above Y=" +
                    plugin.getConfig().getInt("core.min-y-level", 62) + "!"));
            return;
        }

        int maxUnderground = plugin.getConfig().getInt("core.max-underground-depth", 5);
        int highestY = block.getWorld().getHighestBlockYAt(block.getX(), block.getZ());
        if (highestY - block.getY() > maxUnderground) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("The nation core cannot be placed more than "
                    + maxUnderground + " blocks underground! It must be visible."));
            return;
        }

        Location nationSpawn = plugin.getNationManager().getNationSpawn(nationId);
        if (nationSpawn == null) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You must set a nation spawn before placing the core!"));
            player.sendMessage(MessageUtil.warning("Use /nation setspawn at your desired location first."));
            return;
        }

        int maxDistance = plugin.getConfig().getInt("core.max-distance-from-spawn", 10);
        if (!block.getWorld().equals(nationSpawn.getWorld()) ||
                block.getLocation().distance(nationSpawn) > maxDistance) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("The nation core must be placed within " + maxDistance + " blocks of the nation spawn!"));
            return;
        }

        plugin.getNationManager().setNationCore(nationId, block.getLocation());
        player.sendMessage(MessageUtil.success("Nation core placed! This location is permanent and cannot be changed!"));
        player.sendMessage(MessageUtil.warning("Defend it well - if it\'s destroyed, your nation will be disbanded!"));
        player.sendMessage(MessageUtil.success("\u00a7a\u2714 Setup complete! All nation commands are now unlocked."));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        Integer nationId = plugin.getNationManager().getNationByCore(block.getLocation());
        if (nationId == null) return;

        event.setCancelled(true);

        boolean isLeader = plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (isLeader || player.isOp()) {
                plugin.getNationUpgradeGUI().openGUI(player, nationId);
            } else {
                String nationName = plugin.getNationManager().getNationName(nationId);
                player.sendMessage(MessageUtil.prefix("Nation Core", nationName));
                player.sendMessage(MessageUtil.info("Treasury: " + MessageUtil.formatYen(plugin.getNationManager().getTreasury(nationId))));
                player.sendMessage(MessageUtil.info("Citizens: " + plugin.getNationManager().getNationCitizens(nationId).size()));
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            String nationName = plugin.getNationManager().getNationName(nationId);
            player.sendMessage(MessageUtil.prefix("Nation Core", nationName));
            player.sendMessage(MessageUtil.info("Treasury: " + MessageUtil.formatYen(plugin.getNationManager().getTreasury(nationId))));
            player.sendMessage(MessageUtil.info("Citizens: " + plugin.getNationManager().getNationCitizens(nationId).size()));
            if (isLeader || player.isOp()) {
                player.sendMessage(MessageUtil.warning("Right-click to open upgrade menu."));
            }
        }
    }
}
