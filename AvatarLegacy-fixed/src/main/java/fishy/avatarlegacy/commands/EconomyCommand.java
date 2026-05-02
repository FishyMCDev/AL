package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EconomyCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public EconomyCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }
        if (CommandUtil.requiresOp(player)) return true;

        if (args.length < 3) {
            player.sendMessage(MessageUtil.error("Usage: /economy <give|take|set> <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(MessageUtil.error("Player not found!"));
            return true;
        }

        try {
            double amount = Double.parseDouble(args[2]);

            switch (args[0].toLowerCase()) {
                case "give":
                    plugin.getEconomyManager().deposit(target.getUniqueId(), amount);
                    player.sendMessage(MessageUtil.success("Gave " + MessageUtil.formatYen(amount) + " to " + target.getName()));
                    break;

                case "take":
                    if (plugin.getEconomyManager().withdraw(target.getUniqueId(), amount)) {
                        player.sendMessage(MessageUtil.success("Took " + MessageUtil.formatYen(amount) + " from " + target.getName()));
                    } else {
                        player.sendMessage(MessageUtil.error("Player has insufficient funds!"));
                    }
                    break;

                case "set":
                    plugin.getEconomyManager().setBalance(target.getUniqueId(), amount);
                    player.sendMessage(MessageUtil.success("Set " + target.getName() + "'s balance to " + MessageUtil.formatYen(amount)));
                    break;

                default:
                    player.sendMessage(MessageUtil.error("Unknown subcommand!"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.error("Invalid amount!"));
        }

        return true;
    }
}