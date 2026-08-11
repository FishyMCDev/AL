package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Administrative, in-place config reload; does not perform Bukkit's unsafe server reload. */
public final class AvatarLegacyCommand implements CommandExecutor {
    private final AvatarLegacy plugin;
    public AvatarLegacyCommand(AvatarLegacy plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("avatarlegacy.admin")) { sender.sendMessage(MessageUtil.error("No permission.")); return true; }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) { sender.sendMessage(MessageUtil.error("Usage: /avatarlegacy reload")); return true; }
        plugin.reloadConfig();
        MessageUtil.init(plugin.getConfig());
        plugin.getTraitManager().ensureTraitConfig();
        plugin.getSkillTreeManager().loadConfig();
        sender.sendMessage(MessageUtil.success("AvatarLegacy configuration, traits, and skill trees reloaded."));
        return true;
    }
}
