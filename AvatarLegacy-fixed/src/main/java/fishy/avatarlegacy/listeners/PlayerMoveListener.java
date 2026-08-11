package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerMoveListener implements Listener {

    private final AvatarLegacy plugin;

    
    private final Set<UUID> warned = new HashSet<>();

    public PlayerMoveListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isOp()) return;

        UUID uuid = player.getUniqueId();

        
        if (plugin.getSkyFreezeManager().isFrozen(uuid)) {
            event.setCancelled(true);
            plugin.getSkyFreezeManager().enforceFreeze(player);
            if (!warned.contains(uuid)) {
                warned.add(uuid);
                player.sendMessage("§c§l✦ You are frozen in the sky! Use §e/b choose <element> §c§lto be released.");
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> warned.remove(uuid), 60L);
            }
            return;
        }

        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data == null) {
            event.setCancelled(true);
            if (!warned.contains(uuid)) {
                warned.add(uuid);
                player.sendMessage("§c§lYou cannot move yet! §eChoose your bending element with /b choose <element> §cto begin.");
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> warned.remove(uuid), 100L);
            }
            return;
        }

        
        if (!data.hasEverChosen()) {
            event.setCancelled(true);
            if (!warned.contains(uuid)) {
                warned.add(uuid);
                player.sendMessage("§c§lYou must choose an element first! §eUse /b choose <element>§c.");
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> warned.remove(uuid), 100L);
            }
            return;
        }

        
        data.updateActivity();
    }
}

