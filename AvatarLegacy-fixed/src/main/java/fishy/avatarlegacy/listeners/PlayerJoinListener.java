package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerJoinListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().loadPlayerData(player.getUniqueId());

        if (data == null) {
            
            
            plugin.getSkyFreezeManager().freezePlayer(player);

            
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l    WELCOME TO AVATARLEGACY");
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("");
                player.sendMessage("§7You have been suspended in the sky.");
                player.sendMessage("§7Complete both steps below to enter the world:");
                player.sendMessage("");
                player.sendMessage("§a§lSTEP 1 §7— §eCreate your character");
                player.sendMessage("  §7Command: §e/character create <name>");
                player.sendMessage("  §7Example: §e/character create Aang");
                player.sendMessage("");
                player.sendMessage("§a§lSTEP 2 §7— §eChoose your bending element");
                player.sendMessage("  §7Command: §e/b choose <element>");
                player.sendMessage("  §7Elements: §cfire §bwater §aearth §7air §6chi");
                player.sendMessage("");
                player.sendMessage("§7You will drop into the world once Step 2 is done.");
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }, 8L);

        } else {
            data.setLastLogin(System.currentTimeMillis());
            data.setUsername(player.getName());

            String charName = plugin.getCharacterManager().getCharacterName(player.getUniqueId());
            plugin.getCharacterManager().cacheCharacterName(player.getUniqueId(), charName);

            
            if (plugin.getAvatarManager().isAvatar(player.getUniqueId())) {
                
                plugin.getAvatarManager().refreshCachedAvatar();
            }

            if (data.getElement() != null) {
                final String element = data.getElement();
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, element);
                    plugin.getProjectKorraIntegration().grantDefaultMoves(player, element);
                }, 2L);
            }

            if (!data.hasEverChosen()) {
                
                plugin.getSkyFreezeManager().freezePlayer(player);
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§e§l  CHOOSE YOUR ELEMENT");
                    player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("");
                    player.sendMessage("§7You are suspended in the sky.");
                    player.sendMessage("§7Your character §e" + charName + " §7is created,");
                    player.sendMessage("§7but you haven't chosen an element yet.");
                    player.sendMessage("");
                    player.sendMessage("§a§lSTEP 2 §7— §eChoose your bending element");
                    player.sendMessage("  §7Command: §e/b choose <element>");
                    player.sendMessage("  §7Elements: §cfire §bwater §aearth §7air §6chi");
                    player.sendMessage("");
                    player.sendMessage("§7You will drop into the world once this is done.");
                    player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                }, 8L);
            } else {
                
                if (!data.isElementPermanent() && data.getElement() != null) {
                    long remaining = plugin.getElementManager().getTimeUntilPermanent(player.getUniqueId());
                    if (remaining > 0) {
                        long hours   = remaining / 3600;
                        long minutes = (remaining % 3600) / 60;
                        player.sendMessage("§e⏳ Element not yet permanent: §f" + hours + "h " + minutes + "m §7of playtime left.");
                        player.sendMessage("§7Change it with §e/b choose§7, or check time with §e/elementtime§7.");
                    }
                }
            }

            plugin.getNationManager().applyCitizenStrengthBonus(player);
            plugin.getNotificationManager().sendOfflineNotifications(player);
        }

        plugin.getPlaytimeManager().startSession(player);
    }
}
