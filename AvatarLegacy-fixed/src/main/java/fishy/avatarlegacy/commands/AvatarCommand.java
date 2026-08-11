package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class AvatarCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public AvatarCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof org.bukkit.entity.Player p) {
            if (CommandUtil.requiresProfile(plugin, p)) return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> handleInfo(sender);
            case "scores" -> handleScores(sender);
            case "set" -> handleSet(sender, args);
            case "setscore" -> handleSetScore(sender, args);
            case "reset" -> handleReset(sender, args);
            case "cycle" -> handleCycle(sender);
            case "history" -> handleHistory(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleInfo(CommandSender sender) {
        String cycle = plugin.getAvatarManager().getCurrentCycleElement();
        boolean reincarnating = plugin.getAvatarManager().isReincarnationActive();
        UUID currentAvatar = plugin.getAvatarManager().getCurrentAvatar();

        sender.sendMessage(MessageUtil.prefix("Avatar", "Avatar Information"));
        sender.sendMessage(MessageUtil.info("Current Cycle: " + cycle.toUpperCase()));
        sender.sendMessage(MessageUtil.info("Reincarnation Active: " + reincarnating));

        if (currentAvatar != null) {
            Player avatarPlayer = Bukkit.getPlayer(currentAvatar);
            String avatarName = avatarPlayer != null ? avatarPlayer.getName() : "Offline";
            sender.sendMessage(MessageUtil.info("Current Avatar: " + avatarName));
        } else {
            sender.sendMessage(MessageUtil.info("Current Avatar: None (Reincarnation Period)"));
        }

        if (reincarnating) {
            int debuff = plugin.getConfig().getInt("avatar.global-debuff-during-reincarnation", 10);
            sender.sendMessage(MessageUtil.warning("The world suffers a " + debuff + "% bending debuff during reincarnation!"));
        }
    }

    private void handleScores(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.getAvatarManager().updateCandidateScores();
            new fishy.avatarlegacy.guis.AvatarScoresGUI(plugin).open(player);
            return;
        }

        sender.sendMessage(MessageUtil.prefix("Avatar", "Top 10 Avatar Candidates"));

        String currentElement = plugin.getAvatarManager().getCurrentCycleElement();

        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "SELECT ac.uuid, ac.interconnection_score, ac.experience_score, ac.playtime_score, ac.average_score, p.username " +
                "FROM avatar_candidates ac " +
                "JOIN players p ON ac.uuid = p.uuid " +
                "WHERE p.element = ? AND p.element_permanent = 1 AND ac.eligible = 1 " +
                "ORDER BY ac.average_score DESC LIMIT 10";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, currentElement);
            ResultSet rs = stmt.executeQuery();

            int rank = 1;
            boolean found = false;

            while (rs.next()) {
                found = true;
                String username = rs.getString("username");
                int interconnection = rs.getInt("interconnection_score");
                int experience = rs.getInt("experience_score");
                int playtime = rs.getInt("playtime_score");
                double average = rs.getDouble("average_score");

                sender.sendMessage(MessageUtil.info(rank + ". §e" + username
                        + " §7| Score: §a" + String.format("%.0f", average)));
                sender.sendMessage(MessageUtil.info("   §7Playtime: §f" + playtime + "h"
                        + " §7| AdminScore: §f" + interconnection
                        + " §7| XP: §f" + experience));
                rank++;
            }

            if (!found) {
                sender.sendMessage(MessageUtil.warning("No eligible candidates found for " + currentElement.toUpperCase() + " cycle."));
            }

        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Failed to retrieve scores!"));
            plugin.getLogger().severe("Failed to get avatar scores: " + e.getMessage());
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        boolean isAdmin = sender.hasPermission("avatarlegacy.admin") ||
                (sender instanceof org.bukkit.entity.Player p && p.isOp());
        if (!isAdmin) {
            sender.sendMessage(MessageUtil.error("No permission!"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("Usage: /avatar set <player>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.error("Player not found!"));
            return;
        }

        fishy.avatarlegacy.models.PlayerData targetData =
                plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (targetData == null) {
            sender.sendMessage(MessageUtil.error("Player profile data is unavailable!"));
            return;
        }

        String playerElement = targetData.getElement();
        if ("chi".equalsIgnoreCase(playerElement)) {
            sender.sendMessage(MessageUtil.error("Chi users cannot become the Avatar! Chi is not a bending element."));
            return;
        }

        String currentCycle = plugin.getAvatarManager().getCurrentCycleElement();


        boolean force = args.length >= 3 && args[2].equalsIgnoreCase("--force");
        if (!force) {
            if (!currentCycle.equalsIgnoreCase(playerElement)) {
                sender.sendMessage(MessageUtil.error(
                        target.getName() + " is a " + (playerElement != null ? playerElement.toUpperCase() : "unknown")
                                + " bender, but the current cycle is " + currentCycle.toUpperCase() + "!"));
                sender.sendMessage(MessageUtil.warning(
                        "Use §e/avatar set --force " + target.getName() + " §cto override the cycle restriction."));
                return;
            }
        }

        plugin.getAvatarManager().setAvatar(target.getUniqueId());
        sender.sendMessage(MessageUtil.success(target.getName() + " is now the Avatar!"));
        if (force && !currentCycle.equalsIgnoreCase(playerElement)) {
            sender.sendMessage(MessageUtil.warning("[ADMIN] Cycle override used — cycle is "
                    + currentCycle.toUpperCase() + " but player is " + (playerElement != null ? playerElement.toUpperCase() : "unknown") + "."));
        }
    }

    private void handleSetScore(CommandSender sender, String[] args) {
        boolean isAdmin = sender.hasPermission("avatarlegacy.admin") ||
                (sender instanceof org.bukkit.entity.Player p2 && p2.isOp());
        if (!isAdmin) {
            sender.sendMessage(MessageUtil.error("No permission!"));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(MessageUtil.error("Usage: /avatar setscore <player> <interconnection|experience|playtime> <value>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.error("Player not found!"));
            return;
        }

        String scoreType = args[2].toLowerCase();
        int value;

        try {
            value = Integer.parseInt(args[3]);
            if (value < 0 || value > 100) {
                sender.sendMessage(MessageUtil.error("Score must be between 0 and 100!"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.error("Invalid number!"));
            return;
        }

        Connection conn = plugin.getDatabaseManager().getConnection();
        String column;

        switch (scoreType) {
            case "interconnection" -> column = "interconnection_score";
            case "experience" -> column = "experience_score";
            case "playtime" -> column = "playtime_score";
            default -> {
                sender.sendMessage(MessageUtil.error("Invalid score type! Use: interconnection, experience, or playtime"));
                return;
            }
        }



        String upsert = "INSERT OR IGNORE INTO avatar_candidates (uuid, interconnection_score, experience_score, playtime_score, average_score, eligible) VALUES (?, 0, 0, 0, 0, 1)";
        try (PreparedStatement upsertStmt = conn.prepareStatement(upsert)) {
            upsertStmt.setString(1, target.getUniqueId().toString());
            upsertStmt.executeUpdate();
        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Database error on upsert!"));
            plugin.getLogger().severe("Failed to upsert avatar candidate: " + e.getMessage());
            return;
        }

        String sql = "UPDATE avatar_candidates SET " + column + " = ? WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, value);
            stmt.setString(2, target.getUniqueId().toString());
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                String updateAvg = "UPDATE avatar_candidates SET average_score = " +
                        "(interconnection_score + experience_score + playtime_score) / 3.0 " +
                        "WHERE uuid = ?";
                try (PreparedStatement avgStmt = conn.prepareStatement(updateAvg)) {
                    avgStmt.setString(1, target.getUniqueId().toString());
                    avgStmt.executeUpdate();
                }

                sender.sendMessage(MessageUtil.success("Set " + target.getName() + "'s " + scoreType + " score to " + value));
            } else {
                sender.sendMessage(MessageUtil.error("Failed to update score!"));
            }

        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Database error!"));
            plugin.getLogger().severe("Failed to set avatar score: " + e.getMessage());
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        boolean isAdmin = sender.hasPermission("avatarlegacy.admin") ||
                (sender instanceof org.bukkit.entity.Player p3 && p3.isOp());
        if (!isAdmin) {
            sender.sendMessage(MessageUtil.error("No permission!"));
            return;
        }

        UUID currentAvatar = plugin.getAvatarManager().getCurrentAvatar();
        if (currentAvatar != null) {
            plugin.getAvatarManager().stripAvatarState(currentAvatar);
        }

        plugin.getAvatarManager().clearCachedAvatar();

        int days = plugin.getConfig().getInt("avatar.reincarnation-period-days", 7);
        long now = System.currentTimeMillis();
        long endTime = now + (days * 24L * 60 * 60 * 1000);

        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "UPDATE avatar_cycle SET current_avatar_uuid = NULL, reincarnation_active = 1, " +
                "reincarnation_start_timestamp = ?, reincarnation_end_timestamp = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, now);
            stmt.setLong(2, endTime);
            stmt.executeUpdate();

            sender.sendMessage(MessageUtil.success("Avatar status reset! Reincarnation period started."));
            Bukkit.broadcast(MessageUtil.warning("The Avatar has been reset by an admin. The world awaits a new Avatar!"));

        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Failed to reset avatar!"));
            plugin.getLogger().severe("Failed to reset avatar: " + e.getMessage());
        }
    }

    private void handleCycle(CommandSender sender) {
        String currentCycle = plugin.getAvatarManager().getCurrentCycleElement();
        sender.sendMessage(MessageUtil.info("Current Avatar Cycle: " + currentCycle.toUpperCase()));

        java.util.List<String> cycleList = plugin.getConfig().getStringList("avatar.cycle-order");
        String cycleDisplay = cycleList.isEmpty()
                ? "Fire → Air → Water → Earth"
                : String.join(" → ", cycleList.stream()
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .toArray(String[]::new));
        sender.sendMessage(MessageUtil.info("Cycle Order: " + cycleDisplay));
    }

    private void handleHistory(CommandSender sender, String[] args) {

        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Integer.parseInt(args[1])); }
            catch (NumberFormatException ignored) {}
        }
        int pageSize = 5;
        int offset = (page - 1) * pageSize;

        sender.sendMessage(MessageUtil.prefix("Avatar", "Avatar History (Page " + page + ")"));

        Connection conn = plugin.getDatabaseManager().getConnection();


        int total = 0;
        try (PreparedStatement cs = conn.prepareStatement("SELECT COUNT(*) FROM avatar_history")) {
            ResultSet cr = cs.executeQuery();
            if (cr.next()) total = cr.getInt(1);
        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Failed to count avatar history!"));
            plugin.getLogger().severe("Failed to count avatar history: " + e.getMessage());
            return;
        }

        if (total == 0) {
            sender.sendMessage(MessageUtil.warning("No avatar history recorded yet."));
            return;
        }

        String sql = "SELECT ah.avatar_uuid, ah.element, ah.start_timestamp, ah.end_timestamp, ah.death_cause, " +
                "p.username " +
                "FROM avatar_history ah " +
                "LEFT JOIN players p ON ah.avatar_uuid = p.uuid " +
                "ORDER BY ah.start_timestamp DESC " +
                "LIMIT ? OFFSET ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pageSize);
            stmt.setInt(2, offset);
            ResultSet rs = stmt.executeQuery();

            int shown = 0;
            while (rs.next()) {
                shown++;
                String username  = rs.getString("username");
                if (username == null) username = "Unknown";
                String element   = rs.getString("element");
                if (element != null) element = element.substring(0, 1).toUpperCase() + element.substring(1).toLowerCase();
                else element = "Unknown";

                long startMs   = rs.getLong("start_timestamp");
                long endMs     = rs.getLong("end_timestamp");
                String death   = rs.getString("death_cause");

                String startDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(startMs));
                String endDate   = endMs > 0
                        ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(endMs))
                        : "§a(Still Active)";

                long reignDays = endMs > 0
                        ? (endMs - startMs) / 86_400_000L
                        : (System.currentTimeMillis() - startMs) / 86_400_000L;

                sender.sendMessage(MessageUtil.info("§e" + username + " §7[" + element + "]"));
                sender.sendMessage(MessageUtil.info("  §7From: §f" + startDate + " §7→ §f" + endDate
                        + " §7(" + reignDays + " days)"));
                if (death != null && !death.isBlank()) {
                    sender.sendMessage(MessageUtil.info("  §7Death: §c" + death));
                }
            }

            if (shown == 0) {
                sender.sendMessage(MessageUtil.warning("No entries on this page."));
            }

            int totalPages = (int) Math.ceil((double) total / pageSize);
            sender.sendMessage(MessageUtil.info("§7Page §e" + page + "§7/§e" + totalPages
                    + " §7— use §e/avatar history <page> §7to navigate."));

        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Failed to retrieve avatar history!"));
            plugin.getLogger().severe("Failed to get avatar history: " + e.getMessage());
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.prefix("Avatar", "Commands:"));
        sender.sendMessage(MessageUtil.info("/avatar info - View avatar information"));
        sender.sendMessage(MessageUtil.info("/avatar scores - View top 10 candidates"));
        sender.sendMessage(MessageUtil.info("/avatar cycle - View cycle information"));
        sender.sendMessage(MessageUtil.info("/avatar history [page] - View past avatars and their info"));

        boolean senderIsAdmin = sender.hasPermission("avatarlegacy.admin") ||
                (sender instanceof org.bukkit.entity.Player pa && pa.isOp());
        if (senderIsAdmin) {
            sender.sendMessage(MessageUtil.info("/avatar set <player> - Manually set avatar (must match cycle) [ADMIN]"));
            sender.sendMessage(MessageUtil.info("/avatar set --force <player> - Force set avatar, ignoring cycle [ADMIN]"));
            sender.sendMessage(MessageUtil.info("/avatar setscore <player> <type> <value> - Set score [ADMIN]"));
            sender.sendMessage(MessageUtil.info("/avatar reset - Reset avatar status [ADMIN]"));
        }
    }
}