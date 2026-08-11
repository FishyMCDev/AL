package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PlayerCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public PlayerCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("deathbans")) {
            sender.sendMessage(MessageUtil.error("Usage: /player deathbans"));
            return true;
        }

        sender.sendMessage(MessageUtil.prefix("Player", "Global Deathbans"));
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT p.username, pp.deathban_count " +
                        "FROM player_penalties pp " +
                        "LEFT JOIN players p ON p.uuid = pp.uuid " +
                        "WHERE pp.deathban_count > 0 " +
                        "ORDER BY pp.deathban_count DESC, p.username ASC " +
                        "LIMIT 10")) {
            ResultSet rs = stmt.executeQuery();
            int rank = 1;
            while (rs.next()) {
                String name = rs.getString("username");
                if (name == null || name.isBlank()) name = "Unknown";
                sender.sendMessage("§7#" + rank++ + " §e" + name + " §7- §c" + rs.getInt("deathban_count"));
            }
            if (rank == 1) {
                sender.sendMessage("§7No global deathbans recorded yet.");
            }
        } catch (SQLException e) {
            sender.sendMessage(MessageUtil.error("Failed to load global deathbans."));
            plugin.getLogger().warning("Failed to query global deathbans: " + e.getMessage());
        }
        return true;
    }
}
