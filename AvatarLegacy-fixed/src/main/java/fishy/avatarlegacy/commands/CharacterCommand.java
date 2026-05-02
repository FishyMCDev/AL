package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.*;

public class CharacterCommand implements CommandExecutor {

    private final AvatarLegacy plugin;

    public CharacterCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender);
            case "info"   -> handleInfo(sender);
            case "list"   -> handleList(sender, args);
            case "ranking" -> handleRanking(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!")); return;
        }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("You must provide a name. Usage: /character create <n>")); return;
        }
        if (plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) != null) {
            player.sendMessage(MessageUtil.error("You already have an active character!")); return;
        }

        String characterName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (characterName.isEmpty()) {
            player.sendMessage(MessageUtil.error("Character name cannot be blank.")); return;
        }
        if (characterName.length() > 24) {
            player.sendMessage(MessageUtil.error("Character name must be 24 characters or less.")); return;
        }

        plugin.getPlayerDataManager().createPlayerData(player);
        plugin.getCharacterManager().createCharacter(player.getUniqueId(), characterName);
        plugin.getCharacterManager().cacheCharacterName(player.getUniqueId(), characterName);
        plugin.getEconomyManager().createAccount(player.getUniqueId());
        plugin.getStatsManager().createStats(player.getUniqueId());

        long timerHours = plugin.getConfig().getLong("element-selection.timer-hours", 2);
        player.sendMessage("§a§l✔ Character §e" + characterName + " §acreated!");
        player.sendMessage("");
        player.sendMessage("§a§lSTEP 2 §7— §eNow choose your bending element:");
        player.sendMessage("  §7Command: §e/b choose <element>");
        player.sendMessage("  §7Elements: §cfire §bwater §aearth §7air §6chi");
        player.sendMessage("");
        player.sendMessage("§7You have §e" + timerHours + " hours §7of playtime to change your mind after choosing.");
        player.sendMessage("§7After that it §clocks permanently§7. Track it with §e/elementtime§7.");

        plugin.getLuckPermsIntegration().grantPermission(player.getUniqueId(), "bending.command.choose");
        plugin.getLuckPermsIntegration().grantPermission(player.getUniqueId(), "bending.command.rechoose");
        plugin.getPlaytimeManager().startSession(player);
        plugin.getNotificationManager().sendOfflineNotifications(player);

        
        
        
    }

    private void handleDelete(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!")); return;
        }
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(MessageUtil.error("You do not have an active character!")); return;
        }
        if (!plugin.getCharacterManager().canDeleteCharacter(player.getUniqueId())) {
            int cooldownDays = plugin.getConfig().getInt("character.deletion-cooldown-days", 7);
            player.sendMessage(MessageUtil.error(
                    "Cannot delete yet! Element must be permanent and the " + cooldownDays + "-day cooldown must have passed."));
            return;
        }
        
        plugin.getCharacterManager().deleteCharacter(player.getUniqueId());
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!")); return;
        }
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(MessageUtil.error("You do not have an active character! Use /character create <name>."));
            return;
        }
        showCharacterInfo(player, data);
    }

    private void handleList(CommandSender sender, String[] args) {
        String targetName = (args.length >= 2) ? args[1] : (sender instanceof Player p ? p.getName() : null);
        if (targetName == null) {
            sender.sendMessage(MessageUtil.error("Usage: /character list [player]")); return;
        }

        Connection conn = plugin.getDatabaseManager().getConnection();
        try {
            
            String targetUuid = null;
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT uuid FROM players WHERE username = ? COLLATE NOCASE")) {
                s.setString(1, targetName);
                ResultSet r = s.executeQuery();
                if (r.next()) targetUuid = r.getString("uuid");
            }
            if (targetUuid == null) {
                
                for (org.bukkit.OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    if (targetName.equalsIgnoreCase(op.getName())) {
                        targetUuid = op.getUniqueId().toString(); break;
                    }
                }
            }
            if (targetUuid == null) {
                sender.sendMessage(MessageUtil.error("Player not found: " + targetName)); return;
            }

            sender.sendMessage(MessageUtil.prefix("Character History", targetName));
            sender.sendMessage("§8§m------------------------------------------------------");

            int count = 0;

            
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT p.username, c.character_name, c.creation_timestamp, " +
                    "ps.death_count, ps.kill_count, p.playtime_seconds, p.custom_xp " +
                    "FROM characters c " +
                    "JOIN players p ON c.uuid = p.uuid " +
                    "LEFT JOIN player_stats ps ON c.uuid = ps.uuid " +
                    "WHERE c.uuid = ?")) {
                s.setString(1, targetUuid);
                ResultSet r = s.executeQuery();
                if (r.next()) {
                    count++;
                    String charName = r.getString("character_name");
                    boolean isAvatar = plugin.getAvatarManager().isAvatar(java.util.UUID.fromString(targetUuid));
                    String avatarTag = isAvatar ? " §6★AVATAR§r" : "";
                    sender.sendMessage("§a§l▶ §e" + charName + avatarTag + " §a[ACTIVE]");
                    sender.sendMessage("  §7Player: §f" + r.getString("username")
                            + " §7| Deaths: §c" + r.getInt("death_count")
                            + " §7| Kills: §a" + r.getInt("kill_count"));
                    sender.sendMessage("  §7Playtime: §f" + formatPlaytime(r.getLong("playtime_seconds"))
                            + " §7| XP: §f" + r.getInt("custom_xp"));
                }
            }

            
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT * FROM character_history WHERE player_uuid = ? ORDER BY deactivated_timestamp DESC")) {
                s.setString(1, targetUuid);
                ResultSet r = s.executeQuery();
                while (r.next()) {
                    count++;
                    String charName = r.getString("character_name");
                    String reason   = r.getString("inactive_reason");
                    boolean wasAv   = r.getBoolean("was_avatar");
                    String avatarTag = wasAv ? " §6[PREV AVATAR]" : "";
                    String reasonTag = switch (reason != null ? reason : "") {
                        case "AVATAR_DEATH"  -> "§4[Avatar Permadeath]";
                        case "SPIRIT_BROKEN" -> "§8[Spirit Broken]";
                        case "DELETED"       -> "§7[Deleted]";
                        default              -> "§7[Inactive]";
                    };
                    sender.sendMessage("§c§l✖ §7" + charName + avatarTag + " §r" + reasonTag);
                    sender.sendMessage("  §7Player: §f" + r.getString("player_username")
                            + " §7| Deaths: §c" + r.getInt("death_count")
                            + " §7| Kills: §a" + r.getInt("kill_count"));
                    sender.sendMessage("  §7Playtime: §f" + formatPlaytime(r.getLong("playtime_seconds"))
                            + " §7| XP: §f" + r.getInt("custom_xp"));
                }
            }

            if (count == 0) sender.sendMessage("§7No characters found.");
            sender.sendMessage("§8§m------------------------------------------------------");

        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Failed to load character history."));
            plugin.getLogger().severe("Character list error: " + e.getMessage());
        }
    }

    private void handleRanking(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("Usage: /character ranking <xp|kills|playtime> [all] [page]"));
            return;
        }

        String stat = args[1].toLowerCase();
        boolean includeInactive = args.length >= 3 && (args[2].equalsIgnoreCase("all") || (args.length >= 4 && args[3].equalsIgnoreCase("all")));
        int page = 1;
        for (int i = 2; i < args.length; i++) {
            try { page = Math.max(1, Integer.parseInt(args[i])); break; } catch (NumberFormatException ignored) {}
        }

        String column, label;
        switch (stat) {
            case "xp"       -> { column = "custom_xp";        label = "XP"; }
            case "kills"    -> { column = "kill_count";       label = "Kills"; }
            case "playtime" -> { column = "playtime_seconds"; label = "Playtime"; }
            default -> { sender.sendMessage(MessageUtil.error("Valid stats: xp, kills, playtime")); return; }
        }

        int perPage = 3;
        int offset  = (page - 1) * perPage;

        sender.sendMessage(MessageUtil.prefix("Rankings", label + (includeInactive ? " (all)" : "") + " — Page " + page));
        sender.sendMessage("§8§m------------------------------------------------------");

        Connection conn = plugin.getDatabaseManager().getConnection();
        try {
            int rank = offset + 1;
            boolean found = false;

            
            String activeSQL = stat.equals("kills")
                    ? "SELECT p.username, c.character_name, ps." + column +
                      " FROM players p JOIN characters c ON p.uuid = c.uuid " +
                      "LEFT JOIN player_stats ps ON p.uuid = ps.uuid " +
                      "ORDER BY ps." + column + " DESC LIMIT ? OFFSET ?"
                    : "SELECT p.username, c.character_name, p." + column +
                      " FROM players p JOIN characters c ON p.uuid = c.uuid " +
                      "ORDER BY p." + column + " DESC LIMIT ? OFFSET ?";

            try (PreparedStatement s = conn.prepareStatement(activeSQL)) {
                s.setInt(1, perPage); s.setInt(2, offset);
                ResultSet r = s.executeQuery();
                while (r.next()) {
                    found = true;
                    String val = stat.equals("playtime") ? formatPlaytime(r.getLong(column)) : String.valueOf(r.getInt(column));
                    sender.sendMessage("§e" + rank++ + ". §a" + r.getString("character_name")
                            + " §7(" + r.getString("username") + ") — §f" + val + " §a[Active]");
                }
            }

            if (includeInactive) {
                int histOffset = Math.max(0, offset - countActive(conn, stat));
                String histSQL = "SELECT player_username, character_name, " + column +
                        " FROM character_history ORDER BY " + column + " DESC LIMIT ? OFFSET ?";
                try (PreparedStatement s = conn.prepareStatement(histSQL)) {
                    s.setInt(1, perPage); s.setInt(2, histOffset);
                    ResultSet r = s.executeQuery();
                    while (r.next()) {
                        found = true;
                        String val = stat.equals("playtime") ? formatPlaytime(r.getLong(column)) : String.valueOf(r.getInt(column));
                        sender.sendMessage("§8" + rank++ + ". §7" + r.getString("character_name")
                                + " §8(" + r.getString("player_username") + ") — " + val + " §8[Inactive]");
                    }
                }
            }

            if (!found) sender.sendMessage("§7No entries on this page.");
            sender.sendMessage("§8§m------------------------------------------------------");
            sender.sendMessage("§7Use §e/character ranking " + stat + (includeInactive ? " all" : "") + " " + (page + 1) + "§7 for next page.");

        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Failed to load rankings."));
            plugin.getLogger().severe("Ranking error: " + e.getMessage());
        }
    }

    private int countActive(Connection conn, String stat) {
        try (java.sql.Statement s = conn.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM characters")) {
            if (r.next()) return r.getInt(1);
        } catch (SQLException ignored) {}
        return 0;
    }

        private void showCharacterInfo(Player player, PlayerData data) {
        String charName  = plugin.getCharacterManager().getCharacterName(player.getUniqueId());
        String element   = data.getElement() != null ? data.getElement().toUpperCase() : "None";
        String elemStatus = data.getElement() == null ? ""
                : data.isElementPermanent() ? " §a(Permanent)" : " §e(Temporary)";
        String refugeeTag = data.isRefugee() ? " §c[REFUGEE]" : "";

        long pt      = data.getPlaytimeSeconds();
        long hours   = pt / 3600;
        long minutes = (pt % 3600) / 60;

        int deaths   = plugin.getStatsManager().getDeathCount(player.getUniqueId());
        int maxDeath = plugin.getConfig().getInt("bending-spirit.max-deaths", 30);
        int kills    = plugin.getStatsManager().getKillCount(player.getUniqueId());
        int strength = plugin.getStatsManager().getBendingStrength(player.getUniqueId());
        int movesOut = plugin.getStatsManager().getMovesRemoved(player.getUniqueId());
        double bal   = plugin.getEconomyManager().getBalance(player.getUniqueId());

        Integer nationId = plugin.getNationManager().getPlayerNation(data.getUuid());
        String nation = nationId != null
                ? plugin.getNationManager().getNationName(nationId)
                  + (plugin.getNationManager().isNationLeader(data.getUuid(), nationId) ? " (Leader)" : "")
                : "None";

        player.sendMessage(MessageUtil.prefix("Character", (charName != null ? charName : data.getUsername()) + refugeeTag));
        player.sendMessage(MessageUtil.info("Player: §f" + data.getUsername()));
        player.sendMessage(MessageUtil.info("Element: " + element + elemStatus));
        player.sendMessage(MessageUtil.info("Bending Spirit: §c" + deaths + "§7/§a" + maxDeath + " deaths"));
        player.sendMessage(MessageUtil.info("Kills: §a" + kills));
        player.sendMessage(MessageUtil.info("Playtime: " + hours + "h " + minutes + "m"));
        player.sendMessage(MessageUtil.info("Custom XP: " + MessageUtil.formatXP(data.getCustomXP())));
        player.sendMessage(MessageUtil.info("Balance: " + MessageUtil.formatYen(bal)));
        player.sendMessage(MessageUtil.info("Bending Strength: " + strength + "%"));
        player.sendMessage(MessageUtil.info("Moves Removed: " + movesOut));
        player.sendMessage(MessageUtil.info("Nation: " + nation));
        if (plugin.getAvatarManager().isAvatar(data.getUuid())) {
            player.sendMessage(MessageUtil.warning("★ You are the current AVATAR! §c§lDying ends your character permanently!"));
        }
    }

    private void sendUsage(CommandSender s) {
        s.sendMessage(MessageUtil.prefix("Character", "Commands"));
        s.sendMessage(MessageUtil.info("/character create <name> - Create a new character"));
        s.sendMessage(MessageUtil.info("/character info - View your current character"));
        s.sendMessage(MessageUtil.info("/character list [player] - View character history"));
        s.sendMessage(MessageUtil.info("/character ranking <xp|kills|playtime> [all] - Leaderboard"));
        s.sendMessage(MessageUtil.info("/character delete - Voluntarily delete your character"));
    }

    private String formatPlaytime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }
}
