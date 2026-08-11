package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.LocationUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDeathListener implements Listener {
    private final AvatarLegacy plugin;
    private final Map<UUID, Long> recentDeaths = new ConcurrentHashMap<>();
    private static final long DUPLICATE_DEATH_WINDOW_MS = 5_000L;

    public PlayerDeathListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recentDeaths.put(uuid, now);
        if (last != null && now - last < DUPLICATE_DEATH_WINDOW_MS) {
            event.setDeathMessage(null);
            return;
        }

        event.setDeathMessage(null);

        String cause = player.getLastDamageCause() != null ?
                player.getLastDamageCause().getCause().name() : "UNKNOWN";
        String location = LocationUtil.serialize(player.getLocation());

        plugin.getStatsManager().handleDeath(player, cause, location);

        // An Avatar only loses the cycle once their special, three-spirit reserve is exhausted.
        if (plugin.getAvatarManager().isAvatar(uuid) && plugin.getStatsManager().isSpiritBroken(uuid)) {
            plugin.getAvatarManager().handleAvatarDeath(player);
        }
    }
}
