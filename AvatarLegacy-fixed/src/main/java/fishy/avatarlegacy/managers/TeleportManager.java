package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager implements Listener {

    private final AvatarLegacy plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Location> pendingFrom = new HashMap<>();
    private final Map<UUID, Integer> pendingTasks = new HashMap<>();

    public TeleportManager(AvatarLegacy plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (pendingTasks.containsKey(uuid)) {
            cancelPending(uuid);
            player.sendMessage(MessageUtil.error("Teleport cancelled! You took damage."));
        }
    }

    public void requestTeleport(Player player, Location destination, String label) {
        UUID uuid = player.getUniqueId();
        long delaySeconds = plugin.getConfig().getLong("teleport.delay-seconds", 10);
        long cooldownSeconds = plugin.getConfig().getLong("teleport.cooldown-seconds", 10);
        long now = System.currentTimeMillis();

        Long lastTp = cooldowns.get(uuid);
        if (lastTp != null && (now - lastTp) < cooldownSeconds * 1000L) {
            long remaining = (cooldownSeconds * 1000L - (now - lastTp)) / 1000L + 1;
            player.sendMessage(MessageUtil.error("Teleport on cooldown! Available in " + remaining + "s."));
            return;
        }

        if (pendingTasks.containsKey(uuid)) {
            org.bukkit.Bukkit.getScheduler().cancelTask(pendingTasks.get(uuid));
            pendingTasks.remove(uuid);
            pendingFrom.remove(uuid);
            player.sendMessage(MessageUtil.warning("Previous teleport cancelled."));
        }

        pendingFrom.put(uuid, player.getLocation().clone());
        player.sendMessage(MessageUtil.info("Teleporting to " + label + " in " + delaySeconds + "s. Don't move!"));

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                pendingTasks.remove(uuid);
                Location from = pendingFrom.remove(uuid);
                if (from == null || !player.isOnline()) return;

                Location current = player.getLocation();
                if (Math.abs(current.getX() - from.getX()) > 0.5
                        || Math.abs(current.getY() - from.getY()) > 0.5
                        || Math.abs(current.getZ() - from.getZ()) > 0.5) {
                    player.sendMessage(MessageUtil.error("Teleport cancelled! You moved."));
                    return;
                }

                player.teleport(destination);
                cooldowns.put(uuid, System.currentTimeMillis());
                player.sendMessage(MessageUtil.success("Teleported!"));
            }
        }.runTaskLater(plugin, delaySeconds * 20L).getTaskId();

        pendingTasks.put(uuid, taskId);
    }

    public void cancelPending(UUID uuid) {
        Integer taskId = pendingTasks.remove(uuid);
        if (taskId != null) org.bukkit.Bukkit.getScheduler().cancelTask(taskId);
        pendingFrom.remove(uuid);
    }

    public void evict(UUID uuid) {
        cancelPending(uuid);
        cooldowns.remove(uuid);
    }
}
