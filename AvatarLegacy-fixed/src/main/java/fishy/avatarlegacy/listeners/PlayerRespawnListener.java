package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;

public class PlayerRespawnListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerRespawnListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        
        if (data == null) {
            Location spawnRealm = getSpawnRealm();
            if (spawnRealm != null) event.setRespawnLocation(spawnRealm);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.getSkyFreezeManager().freezePlayer(player);
            }, 2L);
            return;
        }

        
        if (!data.hasEverChosen()) {
            Location spawnRealm = getSpawnRealm();
            if (spawnRealm != null) event.setRespawnLocation(spawnRealm);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.getSkyFreezeManager().freezePlayer(player);
            }, 2L);
            return;
        }

        
        if (data.getBedSpawn() != null) {
            event.setRespawnLocation(data.getBedSpawn());
            return;
        }

        Location vanillaBed = player.getBedSpawnLocation();
        if (vanillaBed != null && event.isBedSpawn()) {
            data.setBedSpawn(vanillaBed);
            plugin.getPlayerDataManager().savePlayerData(data);
            event.setRespawnLocation(vanillaBed);
            return;
        }

        
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId != null) {
            Location nationSpawn = plugin.getNationManager().getNationSpawn(nationId);
            if (nationSpawn != null) {
                event.setRespawnLocation(nationSpawn);
                return;
            }
        }

        
        // Element ruins — applies to any player who has an element (permanent or not), except chi
        if (data.getElement() != null && !data.getElement().equalsIgnoreCase("chi")) {
            String path = "spawn-locations." + data.getElement().toLowerCase() + "-ruins";
            ConfigurationSection config = plugin.getConfig().getConfigurationSection(path);
            if (config != null) {
                String worldName = config.getString("world");
                if (worldName != null) {
                    org.bukkit.World w = Bukkit.getWorld(worldName);
                    if (w != null) {
                        event.setRespawnLocation(new Location(w,
                                config.getDouble("x"), config.getDouble("y"), config.getDouble("z")));
                        return;
                    }
                }
            }
        }

        
        Location spawnRealm = getSpawnRealm();
        if (spawnRealm != null) event.setRespawnLocation(spawnRealm);
    }

    private Location getSpawnRealm() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("spawn-locations.spawn-realm");
        if (cfg == null) return null;
        String worldName = cfg.getString("world");
        if (worldName == null) return null;
        org.bukkit.World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, cfg.getDouble("x"), cfg.getDouble("y"), cfg.getDouble("z"));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnChange(PlayerSpawnChangeEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        PlayerSpawnChangeEvent.Cause cause = event.getCause();

        if (cause == PlayerSpawnChangeEvent.Cause.BED) {
            Location newSpawn = event.getNewSpawn();
            if (newSpawn != null) {
                data.setBedSpawn(newSpawn);
                plugin.getPlayerDataManager().savePlayerData(data);
            }
        } else if (cause == PlayerSpawnChangeEvent.Cause.RESET) {
            data.setBedSpawn(null);
            plugin.getPlayerDataManager().savePlayerData(data);
        }
    }
}
