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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerWhoCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public PlayerWhoCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(MessageUtil.error("Usage: /playerwho <player>"));
            return true;
        }

        String targetName = args[0];
        PlayerData data;

        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            data = plugin.getPlayerDataManager().getPlayerData(onlineTarget.getUniqueId());
        } else {
            data = lookupOfflinePlayer(targetName);
        }

        if (data == null) {
            sender.sendMessage(MessageUtil.error("No player profile found for: " + targetName));
            return true;
        }

        String element = data.getElement() != null ? data.getElement().toUpperCase() : "None";
        String elementStatus = data.getElement() == null ? ""
                : data.isElementPermanent() ? " §a(Permanent)" : " §e(Temporary)";
        String refugeeTag = data.isRefugee() ? " §c[REFUGEE]" : "";

        long pt = data.getPlaytimeSeconds();
        long hours = pt / 3600;
        long minutes = (pt % 3600) / 60;

        Integer nationId = plugin.getNationManager().getPlayerNation(data.getUuid());
        String nationInfo = nationId != null
                ? plugin.getNationManager().getNationName(nationId) +
                (plugin.getNationManager().isNationLeader(data.getUuid(), nationId) ? " (Leader)" : "")
                : "None";

        double balance = plugin.getEconomyManager().getBalance(data.getUuid());
        int deaths = plugin.getStatsManager().getDeathCount(data.getUuid());
        int maxDeaths = plugin.getConfig().getInt("bending-spirit.max-deaths", 30);
        int strength = plugin.getStatsManager().getBendingStrength(data.getUuid());
        int movesRemoved = plugin.getStatsManager().getMovesRemoved(data.getUuid());
        int deathbans = plugin.getStatsManager().getDeathbanCount(data.getUuid());

        String spiritColor = deaths >= maxDeaths - 3 ? "§c" : deaths >= maxDeaths / 2 ? "§6" : "§a";
        String spiritBar = spiritColor + deaths + "§7/§c" + maxDeaths;

        sender.sendMessage(MessageUtil.prefix("Player", data.getUsername() + refugeeTag));
        sender.sendMessage(MessageUtil.info("Element: " + element + elementStatus));
        sender.sendMessage(MessageUtil.info("Playtime: " + hours + "h " + minutes + "m"));
        sender.sendMessage(MessageUtil.info("Custom XP: " + MessageUtil.formatXP(data.getCustomXP())));
        sender.sendMessage(MessageUtil.info("Balance: " + MessageUtil.formatYen(balance)));
        sender.sendMessage(MessageUtil.info("Bending Spirit: " + spiritBar + " §7deaths"));
        sender.sendMessage(MessageUtil.info("Bending Strength: " + strength + "%"));
        sender.sendMessage(MessageUtil.info("Moves Removed: " + movesRemoved));
        sender.sendMessage(MessageUtil.info("Deathbans: §c" + deathbans));
        sender.sendMessage(MessageUtil.info("Nation: " + nationInfo));

        if (plugin.getAvatarManager().isAvatar(data.getUuid())) {
            sender.sendMessage(MessageUtil.warning("★ This player is the current AVATAR!"));
        }

        return true;
    }

    private PlayerData lookupOfflinePlayer(String username) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, username, element, element_permanent, playtime_seconds, custom_xp, is_refugee FROM players WHERE LOWER(username) = LOWER(?)")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                PlayerData cached = plugin.getPlayerDataManager().getPlayerData(uuid);
                if (cached != null) return cached;
                PlayerData fallback = new PlayerData(uuid, rs.getString("username"));
                fallback.setElement(rs.getString("element"));
                fallback.setElementPermanent(rs.getBoolean("element_permanent"));
                fallback.setPlaytimeSeconds(rs.getLong("playtime_seconds"));
                fallback.setCustomXP(rs.getInt("custom_xp"));
                fallback.setRefugee(rs.getBoolean("is_refugee"));
                return fallback;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to lookup offline player: " + e.getMessage());
        }
        return null;
    }
}
