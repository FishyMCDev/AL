package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public PayCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }

        if (CommandUtil.requiresCharacter(plugin, player)) return true;

        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Usage: /pay <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(MessageUtil.error("Player not found!"));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(MessageUtil.error("You cannot pay yourself!"));
            return true;
        }

        try {
            double amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                player.sendMessage(MessageUtil.error("Amount must be positive!"));
                return true;
            }

            if (plugin.getEconomyManager().transfer(player.getUniqueId(), target.getUniqueId(), amount)) {
                player.sendMessage(MessageUtil.success("Sent " + MessageUtil.formatYen(amount) + " to " + target.getName()));
                target.sendMessage(MessageUtil.success("Received " + MessageUtil.formatYen(amount) + " from " + player.getName()));
            } else {
                player.sendMessage(MessageUtil.error("Insufficient funds!"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.error("Invalid amount!"));
        }

        return true;
    }
}
