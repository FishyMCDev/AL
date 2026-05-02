package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public StatsCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }

        if (CommandUtil.requiresCharacter(plugin, player)) return true;

        if (args.length > 0 && args[0].equalsIgnoreCase("restore")) {
            plugin.getStatsRestorationGUI().openGUI(player);
            return true;
        }

        int strength = plugin.getStatsManager().getBendingStrength(player.getUniqueId());
        int deaths = plugin.getStatsManager().getDeathCount(player.getUniqueId());
        int movesRemoved = plugin.getStatsManager().getMovesRemoved(player.getUniqueId());

        player.sendMessage(MessageUtil.prefix("Stats", "Your Statistics"));
        player.sendMessage(MessageUtil.info("Bending Strength: " + strength + "%"));
        player.sendMessage(MessageUtil.info("Deaths: " + deaths));
        player.sendMessage(MessageUtil.info("Moves Removed: " + movesRemoved));
        player.sendMessage(MessageUtil.info("Use /stats restore to open restoration GUI"));

        return true;
    }
}