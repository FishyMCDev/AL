package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdminCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    private final Map<UUID, String> pendingDeleteConfirms = new HashMap<>();
    private final Map<UUID, Long>   pendingDeleteExpiry   = new HashMap<>();
    private static final long CONFIRM_EXPIRY_MS = 30_000L;

    public AdminCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    private boolean isAuthorized(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        return player.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAuthorized(sender)) {
            sender.sendMessage(MessageUtil.error("This command is for OPs only!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(MessageUtil.prefix("Admin", "Commands:"));
            sender.sendMessage(MessageUtil.info("/admin setxp <player> <amount>"));
            sender.sendMessage(MessageUtil.info("/admin setplaytime <player> <seconds>"));
            sender.sendMessage(MessageUtil.info("/admin addplaytime <player> <seconds> | addkills <player> <amount>"));
            sender.sendMessage(MessageUtil.info("/admin setelement <player> <element>"));
            sender.sendMessage(MessageUtil.info("/admin resetplayer <player>"));
            sender.sendMessage(MessageUtil.info("/admin deletedata <player>"));
            sender.sendMessage(MessageUtil.info("/admin setprofilename <player> <name>"));
            sender.sendMessage(MessageUtil.info("/admin setmaxdeaths <amount>"));
            sender.sendMessage(MessageUtil.info("/admin setrestorecount <player> <amount>"));
            sender.sendMessage(MessageUtil.info("/admin revivechar <player>"));
            sender.sendMessage(MessageUtil.info("/admin setdeaths <player> <amount>"));
            sender.sendMessage(MessageUtil.info("/admin givechanger <player> [amount]"));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "setxp" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin setxp <player> <amount>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount < 0) { sender.sendMessage(MessageUtil.error("Amount cannot be negative!")); return true; }
                    PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
                    if (data == null) { sender.sendMessage(MessageUtil.error("Player profile is not loaded!")); return true; }
                    data.setCustomXP(amount);
                    plugin.getPlayerDataManager().savePlayerData(data);
                    sender.sendMessage(MessageUtil.success("Set " + target.getName() + "'s XP to " + amount));
                    target.sendMessage(MessageUtil.info("An admin set your XP to " + amount));
                } catch (NumberFormatException e) { sender.sendMessage(MessageUtil.error("Invalid number!")); }
            }

            case "setplaytime" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin setplaytime <player> <seconds>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                try {
                    long seconds = Long.parseLong(args[2]);
                    if (seconds < 0) { sender.sendMessage(MessageUtil.error("Amount cannot be negative!")); return true; }
                    PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
                    if (data == null) { sender.sendMessage(MessageUtil.error("Player profile is not loaded!")); return true; }
                    data.setPlaytimeSeconds(seconds);
                    plugin.getPlayerDataManager().savePlayerData(data);
                    plugin.getElementManager().checkAndMakePermanent(target);
                    plugin.getAvatarManager().updateCandidateScoreForPlayer(target.getUniqueId());
                    long hours   = seconds / 3600;
                    long minutes = (seconds % 3600) / 60;
                    sender.sendMessage(MessageUtil.success("Set " + target.getName() + "'s playtime to "
                            + hours + "h " + minutes + "m (" + seconds + "s)"));
                    target.sendMessage(MessageUtil.info("An admin set your playtime."));
                } catch (NumberFormatException e) { sender.sendMessage(MessageUtil.error("Invalid number! Enter seconds.")); }
            }

            case "addplaytime" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin addplaytime <player> <seconds>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                PlayerData data = target == null ? null : plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
                try {
                    long amount = Long.parseLong(args[2]);
                    if (data == null || amount < 0) throw new NumberFormatException();
                    data.addPlaytimeSeconds(amount); plugin.getPlayerDataManager().savePlayerData(data);
                    sender.sendMessage(MessageUtil.success("Added playtime to " + target.getName()));
                } catch (NumberFormatException e) { sender.sendMessage(MessageUtil.error("Player must be online and seconds must be positive.")); }
            }
            case "addkills" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin addkills <player> <amount>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (target == null || amount < 0) throw new NumberFormatException();
                    for (int i = 0; i < amount; i++) plugin.getStatsManager().incrementKillCount(target.getUniqueId());
                    sender.sendMessage(MessageUtil.success("Added " + amount + " kills to " + target.getName()));
                } catch (NumberFormatException e) { sender.sendMessage(MessageUtil.error("Player must be online and amount must be positive.")); }
            }
            case "setelement" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin setelement <player> <fire|water|earth|air>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                String el = args[2].toLowerCase();
                if (!el.equals("fire") && !el.equals("water") && !el.equals("earth") && !el.equals("air")) {
                    sender.sendMessage(MessageUtil.error("Valid elements: fire, water, earth, air")); return true;
                }
                plugin.getElementManager().changeElement(target, el);
                sender.sendMessage(MessageUtil.success("Set " + target.getName() + "'s element to " + el.toUpperCase()));
            }

            case "resetplayer" -> {
                if (args.length < 2) { sender.sendMessage(MessageUtil.error("Usage: /admin resetplayer <player>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                plugin.getStatsManager().resetStats(target.getUniqueId());
                sender.sendMessage(MessageUtil.success("Reset " + target.getName() + "'s stats (strength, moves, deaths)."));
                target.sendMessage(MessageUtil.info("An admin has reset your stats."));
            }

            case "deletedata" -> {
                if (args.length < 2) { sender.sendMessage(MessageUtil.error("Usage: /admin deletedata <player>")); return true; }
                if (args.length >= 3 && args[2].equalsIgnoreCase("confirm")) {
                    handleDeleteConfirmed(sender, args[1]);
                } else {
                    handleDeleteRequest(sender, args[1]);
                }
            }

            case "setcharname", "setprofilename" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin setprofilename <player> <n>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                plugin.getCharacterManager().setCharacterName(target.getUniqueId(), newName);
                sender.sendMessage(MessageUtil.success("Profile name set to §e" + newName + "§a for " + target.getName()));
            }

            case "setmaxdeaths" -> {
                if (args.length < 2) { sender.sendMessage(MessageUtil.error("Usage: /admin setmaxdeaths <amount>")); return true; }
                try {
                    int amount = Integer.parseInt(args[1]);
                    plugin.getConfig().set("bending-spirit.max-deaths", amount);
                    plugin.saveConfig();
                    sender.sendMessage(MessageUtil.success("Global max deaths set to §e" + amount));
                } catch (NumberFormatException e) { sender.sendMessage(MessageUtil.error("Invalid number.")); }
            }

            case "setrestorecount" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin setrestorecount <player> <amount>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    plugin.getCharacterManager().setRestoreCount(target.getUniqueId(), amount);
                    sender.sendMessage(MessageUtil.success("Restore count set to §e" + amount + "§a for " + target.getName()));
                } catch (NumberFormatException e) { sender.sendMessage(MessageUtil.error("Invalid number.")); }
            }

            case "setdeaths" -> {
                if (args.length < 3) { sender.sendMessage(MessageUtil.error("Usage: /admin setdeaths <player> <amount>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE player_stats SET death_count = ? WHERE uuid = ?")) {
                        stmt.setInt(1, amount);
                        stmt.setString(2, target.getUniqueId().toString());
                        stmt.executeUpdate();
                    }
                    if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(target.getUniqueId());
                    sender.sendMessage(MessageUtil.success("Death count set to §e" + amount + "§a for " + target.getName()));
                } catch (Exception e) { sender.sendMessage(MessageUtil.error("Failed: " + e.getMessage())); }
            }

            case "revivechar" -> {
                if (args.length < 2) { sender.sendMessage(MessageUtil.error("Usage: /admin revivechar <player>")); return true; }
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                if (!op.hasPlayedBefore()) { sender.sendMessage(MessageUtil.error("Player not found.")); return true; }
                if (plugin.getPlayerDataManager().getPlayerData(op.getUniqueId()) != null) {
                    sender.sendMessage(MessageUtil.error(op.getName() + " already has an active player profile!")); return true;
                }
                sender.sendMessage(MessageUtil.success("§e" + op.getName() + "§a can now choose bending with /b choose <element>"));
            }

            case "givechanger" -> {
                if (args.length < 2) { sender.sendMessage(MessageUtil.error("Usage: /admin givechanger <player> [amount]")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(MessageUtil.error("Player not found or offline!")); return true; }
                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Math.max(1, Integer.parseInt(args[2])); }
                    catch (NumberFormatException e) { sender.sendMessage(MessageUtil.error("Invalid amount.")); return true; }
                }
                org.bukkit.inventory.ItemStack changer = fishy.avatarlegacy.items.ElementChangerItem.create(plugin);
                changer.setAmount(amount);
                target.getInventory().addItem(changer);
                sender.sendMessage(MessageUtil.success("Given " + amount + " Element Changer(s) to " + target.getName() + "."));
                target.sendMessage(MessageUtil.info("You received " + amount + " \u00a75\u00a7lElement Changer\u00a7r\u00a77 item(s)! Right-click to use."));
            }

            default -> sender.sendMessage(MessageUtil.error("Unknown subcommand. Use /admin for help."));
        }

        return true;
    }

    private void handleDeleteRequest(CommandSender sender, String targetName) {
        UUID targetUuid = resolveUUID(targetName);
        String resolvedName = resolveName(targetName, targetUuid);

        if (targetUuid == null) {
            sender.sendMessage(MessageUtil.error("Player '" + targetName + "' not found in database!"));
            return;
        }

        if (sender instanceof Player p) {
            pendingDeleteConfirms.put(p.getUniqueId(), targetName);
            pendingDeleteExpiry.put(p.getUniqueId(), System.currentTimeMillis() + CONFIRM_EXPIRY_MS);
        }

        sender.sendMessage(MessageUtil.warning("⚠ WARNING: This will permanently delete ALL data for: §e" + resolvedName));
        sender.sendMessage(MessageUtil.warning("This includes: player profile, economy, stats, deaths, moves, nation, and LuckPerms permissions."));
        sender.sendMessage(MessageUtil.warning("THIS CANNOT BE UNDONE!"));
        sender.sendMessage(MessageUtil.info("Type: §e/admin deletedata " + targetName + " confirm §7within 30 seconds to proceed."));
    }

    private void handleDeleteConfirmed(CommandSender sender, String targetName) {
        if (sender instanceof Player p) {
            String pending = pendingDeleteConfirms.get(p.getUniqueId());
            Long expiry    = pendingDeleteExpiry.get(p.getUniqueId());

            if (pending == null || !pending.equalsIgnoreCase(targetName)) {
                sender.sendMessage(MessageUtil.error("No pending delete request for that player. Run /admin deletedata <player> first."));
                return;
            }
            if (System.currentTimeMillis() > expiry) {
                pendingDeleteConfirms.remove(p.getUniqueId());
                pendingDeleteExpiry.remove(p.getUniqueId());
                sender.sendMessage(MessageUtil.error("Confirmation expired! Run /admin deletedata <player> again."));
                return;
            }

            pendingDeleteConfirms.remove(p.getUniqueId());
            pendingDeleteExpiry.remove(p.getUniqueId());
        }

        UUID targetUuid    = resolveUUID(targetName);
        String resolvedName = resolveName(targetName, targetUuid);

        if (targetUuid == null) {
            sender.sendMessage(MessageUtil.error("Player not found!"));
            return;
        }

        Player online = Bukkit.getPlayer(targetUuid);

        if (online != null) {
            
            
            plugin.getCharacterManager().deactivateCharacter(targetUuid, "ADMIN_RESET");
            plugin.getPlayerDataManager().unloadPlayerData(targetUuid);
        } else {
            
            plugin.getLuckPermsIntegration().revokeAllPermissions(targetUuid);
            plugin.getProjectKorraIntegration().clearAllAbilities(targetUuid);
            deleteAllDataFromDatabase(targetUuid);
            plugin.getPlayerDataManager().unloadPlayerData(targetUuid);
        }

        sender.sendMessage(MessageUtil.success("ALL data for §e" + resolvedName + " §ahas been permanently deleted."));
        plugin.getLogger().info("[AvatarLegacy] " + sender.getName()
                + " deleted ALL data for " + resolvedName + " (" + targetUuid + ")");
    }

    private void deleteAllDataFromDatabase(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String uuidStr  = uuid.toString();

        String[] uuidTables = {
            "players", "protected_moves", "offline_notifications",
            "economy", "player_profiles", "player_stats", "death_log", "avatar_candidates", "player_penalties"
        };

        for (String table : uuidTables) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                stmt.setString(1, uuidStr);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("deletedata: failed on " + table + ": " + e.getMessage());
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM nation_citizens WHERE player_uuid = ?")) {
            stmt.setString(1, uuidStr);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("deletedata: failed on nation_citizens: " + e.getMessage());
        }

        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM kick_cooldowns WHERE player_uuid = ?")) {
            stmt.setString(1, uuidStr);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("deletedata: failed on kick_cooldowns: " + e.getMessage());
        }
    }

    private UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();

        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid FROM players WHERE LOWER(username) = LOWER(?)")) {
            stmt.setString(1, name);
            var rs = stmt.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("uuid"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to resolve UUID for: " + name);
        }
        return null;
    }

    private String resolveName(String fallback, UUID uuid) {
        if (uuid == null) return fallback;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        return data != null ? data.getUsername() : fallback;
    }
}
