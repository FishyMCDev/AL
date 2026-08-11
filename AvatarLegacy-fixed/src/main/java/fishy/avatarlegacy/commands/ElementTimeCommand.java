package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ElementTimeCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public ElementTimeCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }

        if (CommandUtil.requiresProfile(plugin, player)) return true;

        long remaining = plugin.getElementManager().getTimeUntilPermanent(player.getUniqueId());

        if (remaining == 0) {
            player.sendMessage(MessageUtil.success("Your element is already permanent!"));
        } else {
            player.sendMessage(MessageUtil.info("Time until permanent: " + MessageUtil.formatPlaytime(remaining)));
        }

        return true;
    }
}