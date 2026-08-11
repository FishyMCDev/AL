package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class XPCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public XPCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }

        if (CommandUtil.requiresProfile(plugin, player)) return true;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (data != null) {
            player.sendMessage(MessageUtil.info("Your XP: " + MessageUtil.formatXP(data.getCustomXP())));
        }

        return true;
    }
}