package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ServerInfoCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public ServerInfoCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(MessageUtil.prefix("AvatarLegacy", "Server Information"));
        sender.sendMessage(Component.text("Version: 1.0-BETA"));
        sender.sendMessage(Component.text("A world where the Avatar has fallen..."));
        return true;
    }
}

