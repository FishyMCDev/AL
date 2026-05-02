package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SkyFreezeManager {

    private final AvatarLegacy plugin;
    private final Random random = new Random();

    
    private final Map<UUID, GameMode>  frozenPlayers    = new HashMap<>();
    
    private final Map<UUID, Location>  frozenLocations  = new HashMap<>();

    
    private static final int SKY_Y = 255;
    
    private static final int SPREAD = 200;

    public SkyFreezeManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    
    
    

    
    public void freezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (frozenPlayers.containsKey(uuid)) return; 

        
        GameMode previous = player.getGameMode();
        
        if (previous == GameMode.SPECTATOR) previous = GameMode.SURVIVAL;
        frozenPlayers.put(uuid, previous);

        
        String spawnWorldName = plugin.getConfig().getString("spawn-locations.spawn-realm.world", "world");
        World spawnWorld = Bukkit.getWorld(spawnWorldName);
        if (spawnWorld == null) spawnWorld = player.getWorld(); 

        int baseX = (int) plugin.getConfig().getDouble("spawn-locations.spawn-realm.x", 0);
        int baseZ = (int) plugin.getConfig().getDouble("spawn-locations.spawn-realm.z", 0);

        
        int offsetX = (random.nextInt(SPREAD * 2 + 1)) - SPREAD;
        int offsetZ = (random.nextInt(SPREAD * 2 + 1)) - SPREAD;

        Location skyLoc = new Location(spawnWorld, baseX + offsetX + 0.5, SKY_Y, baseZ + offsetZ + 0.5);
        frozenLocations.put(uuid, skyLoc);

        final GameMode finalPrevious = previous;
        final Location finalLoc = skyLoc;

        
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                frozenPlayers.remove(uuid);
                frozenLocations.remove(uuid);
                return;
            }
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(finalLoc);
            
            
        }, 5L);
    }

    
    public void unfreezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        GameMode previous = frozenPlayers.remove(uuid);
        frozenLocations.remove(uuid);
        if (previous == null) return; 

        player.setGameMode(previous);

        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data != null && !data.hasEverChosen()) {
            data.setHasEverChosen(true);
            plugin.getPlayerDataManager().savePlayerData(data);
        }
    }

    
    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    
    public void enforceFreeze(Player player) {
        UUID uuid = player.getUniqueId();
        if (!frozenPlayers.containsKey(uuid)) return;

        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        Location skyLoc = frozenLocations.get(uuid);
        if (skyLoc != null) {
            Location current = player.getLocation();
            if (current.getY() < SKY_Y - 5 ||
                    Math.abs(current.getX() - skyLoc.getX()) > 10 ||
                    Math.abs(current.getZ() - skyLoc.getZ()) > 10) {
                player.teleport(skyLoc);
            }
        }
    }

    
    public void evict(UUID uuid) {
        frozenPlayers.remove(uuid);
        frozenLocations.remove(uuid);
    }
}
