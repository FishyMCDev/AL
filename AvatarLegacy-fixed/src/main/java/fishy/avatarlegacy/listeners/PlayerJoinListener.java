package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Handles player join: loads skill data, sends the resource pack, and runs
 * all existing first-join / returning-player logic.
 */
public class PlayerJoinListener implements Listener {

    private final AvatarLegacy plugin;

    public PlayerJoinListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ── Skill tree data ──────────────────────────────────────────────────
        plugin.getSkillTreeManager().loadPlayerData(player.getUniqueId());

        // ── Hardcoded team-combo moves ───────────────────────────────────────
        // Always granted, independent of element/skill tree/death — see
        // LuckPermsIntegration#grantComboMovePermissions and config.yml's
        // combo-moves list.
        plugin.getLuckPermsIntegration().grantComboMovePermissions(player.getUniqueId());

        // ── Resource pack ────────────────────────────────────────────────────
        sendResourcePack(player);

        // ── Player data ──────────────────────────────────────────────────────
        PlayerData data = plugin.getPlayerDataManager().loadPlayerData(player.getUniqueId());

        if (data == null) {
            // ── Brand-new player ─────────────────────────────────────────────
            plugin.getPlayerDataManager().createPlayerData(player);
            plugin.getEconomyManager().createAccount(player.getUniqueId());
            plugin.getStatsManager().createStats(player.getUniqueId());
            plugin.getLuckPermsIntegration().grantPermission(player.getUniqueId(), "bending.command.choose");
            plugin.getLuckPermsIntegration().grantPermission(player.getUniqueId(), "bending.command.rechoose");

            plugin.getSkyFreezeManager().freezePlayer(player);

            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l    WELCOME TO AVATARLEGACY");
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("");
                player.sendMessage("§7You have been suspended in the sky.");
                player.sendMessage("§7Choose your bending element to enter the world:");
                player.sendMessage("");
                player.sendMessage("§a§lSTEP 1 §7— §eChoose your bending element");
                player.sendMessage("  §7Command: §e/b choose <element>");
                player.sendMessage("  §7Elements: §cfire §bwater §aearth §7air §6chi");
                player.sendMessage("");
                player.sendMessage("§7You will drop into the world once this is done.");
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }, 8L);

        } else {
            // ── Returning player ─────────────────────────────────────────────
            data.setLastLogin(System.currentTimeMillis());
            data.setUsername(player.getName());

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
                    player.sendMessage("§7You haven't chosen an element yet.");
                    player.sendMessage("");
                    player.sendMessage("§a§lSTEP 1 §7— §eChoose your bending element");
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Resource pack sending
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends the skill-tree resource pack from skilltree-config.yml.
     * Config keys:
     *   resource-pack.url     — direct download URL (must end in .zip)
     *   resource-pack.hash    — optional SHA-1 hex (40 chars); leave blank to skip hash check
     *   resource-pack.prompt  — optional message shown in the accept dialog
     *   resource-pack.required — if true, kicks the player on decline (default: false)
     */
    private void sendResourcePack(Player player) {
        org.bukkit.configuration.file.FileConfiguration cfg =
                plugin.getSkillTreeManager().getConfig();

        String url = cfg.getString("resource-pack.url", "").trim();
        if (url.isEmpty()) return; // not configured — skip

        String hash     = cfg.getString("resource-pack.hash", "").trim();
        String prompt   = cfg.getString("resource-pack.prompt",
                "§eThis server uses a resource pack for the Skill Tree GUI.");
        boolean required = cfg.getBoolean("resource-pack.required", false);

        // Use Bukkit's resource-pack API (Paper/Spigot 1.20+)
        // player.setResourcePack accepts (url, hash, prompt, required)
        // hash may be null/empty — Bukkit handles it gracefully
        try {
            if (hash.isEmpty()) {
                player.setResourcePack(url, new byte[0], Component.text(prompt), required);
            } else {
                // Convert hex string to byte array
                byte[] hashBytes = hexToBytes(hash);
                player.setResourcePack(url, hashBytes, Component.text(prompt), required);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SkillTree] Could not send resource pack to "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /** Convert a 40-char SHA-1 hex string to a 20-byte array. */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Resource pack status listener (optional — for kick-on-decline)
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!plugin.getSkillTreeManager().getConfig()
                .getBoolean("resource-pack.required", false)) return;

        switch (event.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD -> {
                Player player = event.getPlayer();
                player.kickPlayer("§cThis server requires the skill tree resource pack.\n" +
                        "§7Please accept the resource pack prompt to play.");
            }
            default -> { /* accepted / downloading — do nothing */ }
        }
    }
}