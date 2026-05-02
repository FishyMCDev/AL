package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class TerritoryEnterListener implements Listener {
    private final AvatarLegacy plugin;

    public TerritoryEnterListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTerritoryEnter(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        if (data.getTerritoriesVisited() == 4) return;

        if (!data.hasVisitedFireTerritory())  checkTerritory(data, to, "fire");
        if (!data.hasVisitedWaterTerritory()) checkTerritory(data, to, "water");
        if (!data.hasVisitedEarthTerritory()) checkTerritory(data, to, "earth");
        if (!data.hasVisitedAirTerritory())   checkTerritory(data, to, "air");
    }

    private void checkTerritory(PlayerData data, Location loc, String element) {
        String path = "element-territories." + element;
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(path);
        if (config == null) return;

        String world = config.getString("world");
        int minX = config.getInt("min-x");
        int maxX = config.getInt("max-x");
        int minZ = config.getInt("min-z");
        int maxZ = config.getInt("max-z");

        if (!LocationUtil.isInTerritory(loc, world, minX, maxX, minZ, maxZ)) return;

        switch (element) {
            case "fire"  -> data.setVisitedFireTerritory(true);
            case "water" -> data.setVisitedWaterTerritory(true);
            case "earth" -> data.setVisitedEarthTerritory(true);
            case "air"   -> data.setVisitedAirTerritory(true);
        }
    }
}
