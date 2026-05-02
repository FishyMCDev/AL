package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class XpConvertCommand implements CommandExecutor {

    private final AvatarLegacy plugin;

    public XpConvertCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }

        if (CommandUtil.requiresCharacter(plugin, player)) return true;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(MessageUtil.error("You do not have an active character!"));
            return true;
        }

        double yenPerXp = plugin.getConfig().getDouble("xp-conversion.yen-per-xp", 100.0);

        if (args.length == 0) {
            double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
            player.sendMessage(MessageUtil.prefix("XP Convert", "Convert Yen to XP"));
            player.sendMessage(MessageUtil.info("Rate: §6" + MessageUtil.formatYen(yenPerXp) + " §7= §a1 XP"));
            player.sendMessage(MessageUtil.info("Your Balance: §6" + MessageUtil.formatYen(balance)));
            player.sendMessage(MessageUtil.info("Your XP: §a" + MessageUtil.formatXP(data.getCustomXP())));
            player.sendMessage(MessageUtil.info("Usage: §e/xpconvert <yen amount>"));
            return true;
        }

        double yenAmount;
        try {
            yenAmount = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.error("Invalid amount! Please enter a number."));
            return true;
        }

        if (yenAmount <= 0) {
            player.sendMessage(MessageUtil.error("Amount must be positive!"));
            return true;
        }

        if (yenPerXp <= 0) {
            player.sendMessage(MessageUtil.error("XP conversion is not configured! Contact an admin."));
            return true;
        }

        int xpGained = (int) Math.floor(yenAmount / yenPerXp);
        if (xpGained <= 0) {
            player.sendMessage(MessageUtil.error("That amount converts to 0 XP. Minimum: "
                    + MessageUtil.formatYen(yenPerXp) + " for 1 XP."));
            return true;
        }

        
        double actualCost = xpGained * yenPerXp;

        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), actualCost)) {
            double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
            player.sendMessage(MessageUtil.error("Insufficient funds! You need "
                    + MessageUtil.formatYen(actualCost)
                    + " but only have " + MessageUtil.formatYen(balance) + "."));
            return true;
        }

        data.addCustomXP(xpGained);
        plugin.getPlayerDataManager().savePlayerData(data);

        player.sendMessage(MessageUtil.success("Converted §6" + MessageUtil.formatYen(actualCost)
                + " §ainto §e" + xpGained + " XP§a!"));
        player.sendMessage(MessageUtil.info("New XP balance: §a" + MessageUtil.formatXP(data.getCustomXP())));

        return true;
    }
}
