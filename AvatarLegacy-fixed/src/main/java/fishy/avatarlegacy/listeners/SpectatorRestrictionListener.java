package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpectatorRestrictionListener implements Listener {

    private final AvatarLegacy plugin;

    private final Set<UUID> warnedHotbar   = new HashSet<>();
    private final Set<UUID> warnedSpectate = new HashSet<>();

    public SpectatorRestrictionListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (player.getGameMode() != GameMode.SPECTATOR) return;
        if (!plugin.getSkyFreezeManager().isFrozen(player.getUniqueId())) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        if (!warnedHotbar.contains(uuid)) {
            warnedHotbar.add(uuid);
            player.sendMessage(MessageUtil.error("You cannot use the hotbar while choosing your element!"));
            player.sendMessage(MessageUtil.warning("Use §e/b choose <element>§6 to be released."));
            Bukkit.getScheduler().runTaskLater(plugin, () -> warnedHotbar.remove(uuid), 60L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!plugin.getSkyFreezeManager().isFrozen(player.getUniqueId())) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        if (!warnedSpectate.contains(uuid)) {
            warnedSpectate.add(uuid);
            player.sendMessage(MessageUtil.error("You cannot spectate other players while choosing your element!"));
            player.sendMessage(MessageUtil.warning("Use §e/b choose <element>§6 to be released."));
            Bukkit.getScheduler().runTaskLater(plugin, () -> warnedSpectate.remove(uuid), 60L);
        }
    }
}
