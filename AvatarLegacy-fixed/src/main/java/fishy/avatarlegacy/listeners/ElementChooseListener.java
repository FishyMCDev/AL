package fishy.avatarlegacy.listeners;

import com.projectkorra.projectkorra.event.PlayerChangeElementEvent;
import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ElementChooseListener implements Listener {

    private final AvatarLegacy plugin;
    private final Random random = new Random();

    
    private final Map<UUID, Long> chooseCooldowns = new HashMap<>();

    public ElementChooseListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onElementChoose(PlayerChangeElementEvent event) {
        if (event.getResult() != PlayerChangeElementEvent.Result.CHOOSE) return;

        Player player = event.getTarget().getPlayer();
        if (player == null || !player.isOnline()) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (data == null) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You cannot choose an element without an active character!"));
            player.sendMessage(MessageUtil.warning("Use \u00a7e/character create <n>\u00a76 to begin."));
            return;
        }

        if (data.isElementPermanent()) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("Your element is permanent and cannot be changed!"));
            player.sendMessage(MessageUtil.info("Use an \u00a75Element Changer\u00a77 item to change your element."));
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean isFirstChoice = (data.getElement() == null);

        
        if (!isFirstChoice) {
            long cooldownSeconds = plugin.getConfig().getLong("element-selection.choose-cooldown-seconds", 60);
            Long lastChoose = chooseCooldowns.get(uuid);
            if (lastChoose != null) {
                long elapsed = (System.currentTimeMillis() - lastChoose) / 1000L;
                if (elapsed < cooldownSeconds) {
                    long remaining = cooldownSeconds - elapsed;
                    event.setCancelled(true);
                    player.sendMessage(MessageUtil.error("You must wait §e" + remaining + "s§c before choosing again!"));
                    return;
                }
            }
        }

        String element = event.getElement().getName().toLowerCase();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getElementManager().setElement(player, element);

            
            
            if (element.equals("chi")) {
                spawnChiPlayerRandomly(player);
            }

            if (plugin.getProjectKorraIntegration().isScrollsEnabled()) {
                List<String> defaultMoves = plugin.getConfig()
                        .getStringList("protected-default-moves." + element);
                for (String move : defaultMoves) {
                    plugin.getScrollManager().giveScrollForUnlock(player, move);
                }
            }

            
            chooseCooldowns.put(uuid, System.currentTimeMillis());

            
            if (plugin.getSkyFreezeManager().isFrozen(uuid)) {
                plugin.getSkyFreezeManager().unfreezePlayer(player);
                player.sendMessage("§a§l✔ You have been released! Welcome to the world.");
            }

            long timerHours = plugin.getConfig().getLong("element-selection.timer-hours", 2);
            if (isFirstChoice) {
                player.sendMessage("§a§l✔ You have chosen §e" + element.toUpperCase() + "§a!");
                player.sendMessage("§7You have §e" + timerHours + " hours §7of active playtime to change your mind with §e/b choose§7.");
                player.sendMessage("§7After that, your element §clocks permanently§7. Track time with §e/elementtime§7.");
            } else {
                player.sendMessage("§a§l✔ Element changed to §e" + element.toUpperCase() + "§a.");
                long remaining = plugin.getElementManager().getTimeUntilPermanent(uuid);
                long hours = remaining / 3600;
                long mins  = (remaining % 3600) / 60;
                player.sendMessage("§7Time until element locks: §e" + hours + "h " + mins + "m §7(use §e/elementtime §7to check).");
            }
        }, 1L);
    }

    
    public void evict(UUID uuid) {
        
        
    }

    private void spawnChiPlayerRandomly(Player player) {
        int radius = plugin.getConfig().getInt("chi.spawn-radius", 1000);
        String worldName = plugin.getConfig().getString("chi.spawn-world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = findSafeChiSpawn(world, radius);
        if (loc == null) {
            
            for (int attempt = 0; attempt < 50; attempt++) {
                int x = random.nextInt(radius * 2 + 1) - radius;
                int z = random.nextInt(radius * 2 + 1) - radius;
                int y = world.getHighestBlockYAt(x, z) + 1;
                loc = new Location(world, x + 0.5, y, z + 0.5);
                if (!isDangerousBlock(world.getBlockAt(x, y - 1, z).getType())) break;
            }
        }
        if (loc == null) return;

        player.teleport(loc);
        player.setBedSpawnLocation(loc, true);
    }

    
    private Location findSafeChiSpawn(World world, int radius) {
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = random.nextInt(radius * 2 + 1) - radius;
            int z = random.nextInt(radius * 2 + 1) - radius;
            int highY = world.getHighestBlockYAt(x, z);
            org.bukkit.block.Block surface = world.getBlockAt(x, highY, z);
            org.bukkit.Material surfaceType = surface.getType();

            
            if (isDangerousBlock(surfaceType)) continue;
            if (surfaceType.name().contains("WATER") || surfaceType.name().contains("LAVA")) continue;
            if (!surfaceType.isSolid()) continue;

            
            org.bukkit.Material above1 = world.getBlockAt(x, highY + 1, z).getType();
            org.bukkit.Material above2 = world.getBlockAt(x, highY + 2, z).getType();
            if (!above1.isAir() && above1 != org.bukkit.Material.CAVE_AIR) continue;
            if (!above2.isAir() && above2 != org.bukkit.Material.CAVE_AIR) continue;

            return new Location(world, x + 0.5, highY + 1, z + 0.5);
        }
        return null;
    }

    private boolean isDangerousBlock(org.bukkit.Material mat) {
        if (mat == null) return true;
        String name = mat.name();
        return name.contains("WATER") || name.contains("LAVA")
                || mat == org.bukkit.Material.FIRE
                || mat == org.bukkit.Material.CAMPFIRE
                || mat == org.bukkit.Material.SOUL_CAMPFIRE
                || mat == org.bukkit.Material.MAGMA_BLOCK
                || mat == org.bukkit.Material.WITHER_ROSE
                || mat == org.bukkit.Material.CACTUS
                || mat == org.bukkit.Material.SWEET_BERRY_BUSH
                || mat == org.bukkit.Material.VOID_AIR;
    }

    private void setSpawnPoint(Player player, String element) {
        String path = "spawn-locations." + element + "-ruins";
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection(path);
        if (cfg == null) return;
        String worldName = cfg.getString("world");
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        Location loc = new Location(world, cfg.getDouble("x"), cfg.getDouble("y"), cfg.getDouble("z"));
        player.setBedSpawnLocation(loc, true);
    }
}

